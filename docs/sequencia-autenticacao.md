# Diagrama de Sequência — Autenticação (CPF → API Gateway → Lambda → JWT → API)

> Cobre o login do **cliente** por CPF (`POST /auth/cliente`) e o uso do token emitido para
> chamar uma rota protegida da API (`GET /api/minhas-ordens-servico`), conforme pedido em
> `docs/requisitos-fase3.md` ("fluxo de autenticação"). O login de **funcionário**
> (`POST /auth/funcionario`, e-mail + senha) e o **primeiro acesso** do cliente
> (`POST /auth/cliente/primeiro-acesso`) passam pelo mesmo desenho até a Lambda; as
> diferenças estão nas notas abaixo. Decisão documentada em
> [RFC-0003](rfc/0003-estrategia-de-autenticacao-cpf-via-lambda.md).

```mermaid
sequenceDiagram
    actor Cliente as Cliente (front-end)
    participant GW as API Gateway (HTTP API)
    participant Auth as JWT Authorizer (Gateway)
    participant Lambda as Lambda oficina-auth
    participant SM as Secrets Manager
    participant DB as PostgreSQL (RDS) — tabela cliente
    participant VL as VPC Link → NLB interno
    participant API as MinhasOrdensServicoController
    participant Sec as JwtDecoder (SecurityConfig)

    Note over Cliente,DB: 1. Login por CPF
    Cliente->>GW: POST /auth/cliente {cpf, senha}
    GW->>Lambda: AWS_PROXY (rota pública, sem authorizer)
    Lambda->>SM: ler JWT_PRIVATE_KEY / DB_PASSWORD (cacheado no runtime)
    Lambda->>DB: SELECT id, status, senha_hash WHERE cpf_cnpj = :cpf
    DB-->>Lambda: cliente (ou nenhuma linha)
    Lambda->>Lambda: bcrypt.compare(senha, senha_hash ?? hash fictício)
    alt cliente não encontrado OU senha não confere
        Lambda-->>GW: 401 problem+json "Credenciais inválidas"
        GW-->>Cliente: 401
    else senha confere, mas status != ATIVO
        Lambda-->>GW: 403 problem+json "Cliente inativo"
        GW-->>Cliente: 403
    else senha confere e status = ATIVO
        Lambda->>Lambda: assina JWT RS256 (sub=clienteId, roles=["CLIENTE"], aud="oficina-api", iss=https://<gateway>, exp=+1h)
        Lambda-->>GW: 200 {token, expiresIn: 3600}
        GW-->>Cliente: 200 {token, expiresIn: 3600}
    end

    Note over Cliente,Sec: 2. Consumo de rota protegida com o token
    Cliente->>GW: GET /api/minhas-ordens-servico (Bearer token)
    GW->>Auth: valida token (rota /api/{proxy+} exige JWT)
    Auth->>Lambda: GET /.well-known/openid-configuration (cacheado pelo Gateway)
    Lambda-->>Auth: {issuer, jwks_uri}
    Auth->>Lambda: GET /.well-known/jwks.json
    Lambda-->>Auth: chave pública (kid, RS256)
    Auth->>Auth: valida assinatura, aud=oficina-api, exp
    alt token inválido, expirado ou ausente
        Auth-->>Cliente: 401 (rejeitado no próprio Gateway, nunca chega na API)
    end
    Auth-->>GW: token válido (claims sub, roles)
    GW->>VL: HTTP_PROXY via VPC Link
    VL->>API: GET /api/minhas-ordens-servico (Bearer token)
    API->>Sec: valida token de novo (assinatura RSA local + aud + iss)
    Sec-->>API: token válido, ROLE_CLIENTE
    alt role não é CLIENTE nesta rota
        Sec-->>Cliente: 403
    end
    API->>API: clienteId = jwt.sub
    API-->>VL: 200 [OrdemServicoResponse...]
    VL-->>GW: 200
    GW-->>Cliente: 200 [OrdemServicoResponse...]
```

## Notas sobre o fluxo real

- **Login de funcionário** (`POST /auth/funcionario`) segue a mesma forma, mas troca a
  tabela `cliente` pelo join `users`/`users_roles`/`roles`, o token dura 8h (28800s) em vez
  de 1h, `roles` vem do banco em vez de ser fixo `["CLIENTE"]`, e `sub` é o `username`
  (e-mail) em vez do id. Rotas de funcionário (`/api/**` fora de
  `/api/minhas-ordens-servico`) exigem `ADMIN`, `RECEPCAO` ou `OPERADOR`.
- **Primeiro acesso** (`POST /auth/cliente/primeiro-acesso`) troca o `SELECT` + `bcrypt.compare`
  por um único `UPDATE cliente SET senha_hash = :hash WHERE status = 'ATIVO' AND email =
  :email AND senha_hash IS NULL RETURNING id` — atômico, então só pode ser executado uma
  vez por cliente; uma segunda tentativa (ou CPF/e-mail que não batem) devolve `422`. Em
  caso de sucesso, devolve `201` e já emite o token (mesmo formato do login).
- O token é validado **duas vezes por caminhos independentes**: o authorizer do API Gateway
  usa o JWKS que a própria Lambda publica; a API usa a chave pública fixa injetada via
  Secrets Manager. Um token forjado ou expirado é rejeitado já no Gateway, sem nunca chegar
  à API.
- `cliente` inexistente e senha errada devolvem a **mesma resposta 401** — e a checagem de
  senha roda contra um hash fictício de custo idêntico quando o cliente não existe
  (`bcrypt` de tempo constante), para não vazar, por tempo de resposta, se um CPF está
  cadastrado. O status `INATIVO` só é revelado depois que a senha confere.
- O `iss` do token nunca é fixo em código: a Lambda o deriva do domínio da própria
  requisição (`https://${event.requestContext.domainName}`), e a API espera esse mesmo
  valor via `JWT_ISSUER`/`APP_PUBLIC_BASE_URL` — ver [RFC-0003](rfc/0003-estrategia-de-autenticacao-cpf-via-lambda.md).
