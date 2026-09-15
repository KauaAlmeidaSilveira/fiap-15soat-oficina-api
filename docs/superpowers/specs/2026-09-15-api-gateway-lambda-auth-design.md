# API Gateway e Lambda de Autenticação — Design

> Tech Challenge Fase 3 · Seção "Autenticação e API Gateway"
> Data: 2026-09-15
> Status: design aprovado, aguardando plano de implementação

## Contexto

A Fase 3 exige um API Gateway para controle e roteamento, proteção das rotas sensíveis com
autenticação via CPF e uma Function Serverless que valide o CPF do cliente, consulte existência e
status do cliente na base e devolva um JWT válido para as APIs protegidas.

Estado atual do código:

- **Só funcionários fazem login.** `POST /api/auth/login` (`AuthUseCase.login`) autentica
  `username` (e-mail) + senha contra a tabela `users` e emite JWT RS256 com a claim `roles`
  (`ADMIN`, `RECEPCAO`, `OPERADOR`).
- **O cliente não tem credencial nem rota própria.** O único contato dele com o sistema é o link
  público `GET /aprovacao-os?token=`, recebido por e-mail.
- `Cliente` já tem `status` (`ATIVO`/`INATIVO`), entregue no PR #2 (`8c11eea`).
- A API é exposta por um `Service` `LoadBalancer` público, e o RDS vive em subnet privada.
- O AWS Academy bloqueia `iam:CreateRole`: toda role usada é a `LabRole` pré-existente.

### Falha existente que este design fecha

O token do link de aprovação (`AprovacaoTokenGatewayImpl`) é assinado com o mesmo par RSA, vale
7 dias e não tem `aud`. O `NimbusJwtDecoder` da API não confere emissor nem audiência, então esse
token é aceito como token de sessão: quem recebe o e-mail de orçamento consegue, por 7 dias, chamar
as 14 rotas `GET` sem `@PreAuthorize` e ler clientes, OS, veículos e produtos de todo mundo.

## Decisões

| Decisão | Escolha | Razão |
|---|---|---|
| Quem se autentica por CPF | O **cliente da oficina** | O enunciado liga o termo a CPF e status, que só `Cliente` tem. Funcionários não têm CPF. |
| Fator de autenticação do cliente | **CPF + senha** | CPF é dado quase público: identifica, mas não autentica. O enunciado pede autenticação "via" CPF, não "somente" CPF. |
| O que o cliente acessa | **Leitura das próprias OS** | Caso de uso real ("em que etapa está meu carro?") sem expor dados de terceiros. Aprovação continua pelo e-mail. |
| Como o cliente ganha senha | **Primeiro acesso na Lambda** (CPF + e-mail do cadastro + nova senha) | Sem SMTP na Lambda e demonstrável em segundos. |
| Login de funcionário | **Também migra para a Lambda** | Um único emissor de tokens de sessão, um único lugar para auditar. |
| Runtime da Lambda | **Node.js 22** | Cold start de ~300 ms na VPC; `bcryptjs` lê os hashes `$2a$` já gravados pelo Spring. |
| Gateway | **AWS API Gateway HTTP API** + JWT authorizer nativo + VPC Link para NLB interno | Validação de token sem código; a API sai da internet e o Gateway vira a única porta. |
| Onde mora o código | Lambda **e** Gateway em `fiap-15soat-oficina-lambda-auth` | O Gateway não tem repositório próprio entre os 4, e é a Lambda que ele expõe. |

### Alternativas descartadas

- **Só CPF, sem senha:** leitura mais literal do enunciado, mas qualquer pessoa com um CPF veria as
  OS de outra.
- **CPF + código por e-mail:** exige guardar código com expiração e enviar e-mail da Lambda; o SES
  do Academy fica em sandbox.
- **Gateway com integração HTTP no ELB público:** o Gateway poderia ser contornado chamando o ELB
  direto, e `/actuator/prometheus` continuaria exposto.
- **REST API (v1) + Lambda authorizer:** usage plans e WAF não são exigidos, e o custo por
  requisição é ~3,5× maior, com um segundo código só para validar token.
- **Java 21 na Lambda:** cold start de 3–6 s com JDBC + BCrypt na VPC.

## Arquitetura

```
cliente/funcionário ──HTTPS──▶ API Gateway (HTTP API)
                                ├─ POST /auth/*                  ──▶ Lambda oficina-auth ──▶ RDS
                                ├─ GET  /.well-known/*           ──▶ Lambda oficina-auth
                                └─ /api/** [JWT authorizer] ──VPC Link──▶ NLB interno ──▶ pods da API ──▶ RDS
```

| Componente | Responsabilidade |
|---|---|
| Lambda `oficina-auth` | Emite **todos** os tokens de sessão; serve discovery e JWKS |
| API Gateway HTTP API | Porta de entrada única; roteamento, throttling e validação do JWT |
| NLB interno | Criado pelo `Service` do K8s; recebe só o tráfego do VPC Link |
| API Java | Revalida o token, aplica autorização por role e continua emitindo **apenas** o token de aprovação por e-mail |

## Contrato dos tokens

Tokens de sessão, RS256, com `kid` no header:

| Claim | Funcionário | Cliente |
|---|---|---|
| `iss` | URL do Gateway (`https://<api-id>.execute-api.us-east-1.amazonaws.com`) | igual |
| `aud` | `oficina-api` | igual |
| `sub` | `username` | id do cliente (string) |
| `roles` | `["ADMIN"]`, `["RECEPCAO"]`, `["OPERADOR"]` | `["CLIENTE"]` |
| `exp` | 8 h | 1 h |

Token de aprovação por e-mail: **inalterado** (`iss = back-end`, `sub = os-aprovacao`, sem `aud`).

**Nenhum dos dois vale no lugar do outro:**

- O authorizer do Gateway exige `iss` e `aud` de sessão, então rejeita o token de aprovação na borda.
- A API exige `aud = oficina-api` no resource server, e o `AprovacaoTokenGatewayImpl` rejeita tokens
  que tenham `aud`. A proteção vale mesmo para quem chegue direto ao pod.

**Chaves:** Lambda e API usam o **mesmo par RSA** (GitHub Secret `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`).
A API continua precisando da chave privada para assinar o token de aprovação.

## Lambda `oficina-auth`

### Rotas

| Rota | Corpo | Sucesso |
|---|---|---|
| `POST /auth/funcionario` | `{username, senha}` | `200 {token, expiresIn}` |
| `POST /auth/cliente` | `{cpf, senha}` | `200 {token, expiresIn}` |
| `POST /auth/cliente/primeiro-acesso` | `{cpf, email, senha}` | `201 {token, expiresIn}` |
| `GET /.well-known/openid-configuration` | — | `200` com `issuer` e `jwks_uri` |
| `GET /.well-known/jwks.json` | — | `200` com a chave pública |

### Login do cliente

Avaliado nesta ordem:

1. Normaliza o CPF para só dígitos e valida os dígitos verificadores.
   - CPF inválido → **400**.
   - Documento com 14 dígitos (CNPJ) → **400** "login disponível apenas para CPF". Clientes PJ ficam
     fora do login nesta fase.
2. Busca o cliente por `cpf_cnpj`. Cliente inexistente, sem `senha_hash` ou senha errada → **a mesma
   resposta 401** "CPF ou senha inválidos", sem revelar quais CPFs são clientes.
3. Só com a senha conferida verifica o `status`. `INATIVO` → **403**.

### Primeiro acesso

1. CPF inválido ou senha com menos de 8 caracteres → **400**.
2. Grava com um único comando atômico:
   ```sql
   UPDATE cliente SET senha_hash = $1
    WHERE cpf_cnpj = $2 AND status = 'ATIVO'
      AND lower(email) = lower($3) AND senha_hash IS NULL
   RETURNING id
   ```
3. Zero linhas afetadas (cliente inexistente, inativo, e-mail divergente ou senha já definida) →
   **a mesma resposta 422** genérica.
4. Uma linha afetada → emite o token do cliente e responde **201**.

**Risco aceito:** quem conhecer CPF e e-mail de um cliente sem senha pode ativar a conta antes dele.
Não há rota de redefinição de senha nesta fase. Os dois pontos entram na RFC de estratégia de
autenticação.

### Login do funcionário

`JOIN` entre `users`, `users_roles` e `roles` pelo `username` normalizado (trim + minúsculas, como
o `AuthUseCase` atual). Usuário inexistente ou senha errada → **401** genérico. Os usuários semeados
pelo `DataLoader` continuam válidos sem migração.

### Formato de erro e logs

- Erros em `application/problem+json` (RFC 7807), no mesmo formato do `GlobalExceptionHandler`.
- Logs em JSON no stdout (CloudWatch), incluindo o `X-Correlation-Id` recebido quando presente.
  Nunca logar senha, hash ou token.

### Estrutura

```
src/handler.js       roteia por método + rota; traduz erros de domínio em HTTP
src/cpf.js           normalização e validação de dígitos verificadores
src/autenticacao.js  regras de negócio; recebe repositório, senhas e tokens por injeção
src/repositorio.js   pg; Pool com 1 conexão, criado fora do handler e reaproveitado entre invocações
src/senhas.js        bcryptjs, custo 10 (igual ao BCryptPasswordEncoder padrão)
src/tokens.js        jose; assina tokens e exporta o JWKS
src/segredos.js      Secrets Manager; cache no escopo do módulo, lido uma vez por cold start
```

Dependências de produção: `pg`, `bcryptjs`, `jose`, `@aws-sdk/client-secrets-manager`.

### Testes

- **Unitários** (`node --test`): `cpf.js` e `autenticacao.js` com dependências falsas, cobrindo cada
  linha das tabelas de resposta acima.
- **Integração:** `repositorio.js` contra Postgres real (service container no GitHub Actions),
  com o schema das tabelas `cliente`, `users`, `roles` e `users_roles` equivalente ao gerado pelo
  Hibernate. Inclui o `UPDATE` concorrente do primeiro acesso.

## Mudanças na API Java

### Modelo

- `dataprovider.persistence.entity.Cliente` ganha `senhaHash` (`senha_hash`, nullable, `length = 60`).
  O Hibernate cria a coluna pelo `ddl-auto`; a Lambda nunca executa DDL.
- O `Cliente` de domínio **não** ganha o campo.
- `ClientePersistenceMapper`: `@Mapping(target = "senhaHash", ignore = true)` em `toEntity` e em
  `updateEntity`. Sem isso, um `PUT /api/clientes/{id}` apagaria a senha do cliente.

### Autorização

Regras de URL no `SecurityConfig`, antes do `anyRequest()`:

```java
.requestMatchers("/api/minhas-ordens-servico/**").hasRole("CLIENTE")
.requestMatchers("/api/**").hasAnyRole("ADMIN", "RECEPCAO", "OPERADOR")
```

- A URL separa **cliente de funcionário**, e o `@PreAuthorize` continua separando **perfis de
  funcionário** entre si.
- `/api/auth/**` sai da lista de rotas públicas.
- O `CLAUDE.md` é atualizado para descrever essa divisão, que substitui a regra "toda autorização
  por role é `@PreAuthorize`".

### Rotas do cliente

`MinhasOrdensServicoController`:

| Rota | Use case | Observação |
|---|---|---|
| `GET /api/minhas-ordens-servico` | `listarPorCliente(clienteId)` (existente) | — |
| `GET /api/minhas-ordens-servico/{id}` | `buscarDoCliente(osId, clienteId)` (novo) | OS de outro cliente → 404, não 403 |

O `clienteId` vem sempre do `sub` do token (`@AuthenticationPrincipal Jwt`), nunca de parâmetro.
A resposta reaproveita `OrdemServicoResponse`.

### Tokens

- **Decoder do resource server:** `NimbusJwtDecoder` com validadores de `aud = oficina-api` e
  `iss = ${jwt.issuer}` (`JWT_ISSUER`), além dos de tempo padrão.
- **`AprovacaoTokenGatewayImpl`:** decoder próprio, construído com a chave pública, que exige
  `iss = back-end`, `sub = os-aprovacao` e ausência de `aud`.
- **Perfis `dev` e `test`:** `jwt.issuer` recebe um valor fixo local.

### Remoções

- `POST /api/auth/login`, `AuthUseCase.login`, `ResultadoLogin`, `TokenAutenticacaoGateway` e
  `TokenAutenticacaoGatewayImpl`, `LoginRequest`, `LoginResponse`, e os testes correspondentes.
- **Permanece** `POST /api/auth/register` (cadastro de funcionário por ADMIN).

### Testes

| Teste | Verifica |
|---|---|
| `OrdemServicoUseCaseUnitTest` | `buscarDoCliente`: dono recebe a OS; outro cliente recebe `RecursoNaoEncontradoException` |
| `MinhasOrdensServicoControllerIT` | lista e detalhe com role `CLIENTE`; funcionário recebe 403 |
| `ClienteControllerIT` / `OrdemServicoControllerIT` | token com role `CLIENTE` recebe 403 em rotas de funcionário |
| Integração de `ClienteGatewayImpl` | `salvar` de cliente existente preserva `senha_hash` |
| Teste do decoder de sessão | token de aprovação e token sem `aud` são rejeitados |
| `AprovacaoTokenGatewayImplUnitTest` | token com `aud` é rejeitado |

O gate JaCoCo de 80% cobre o controller e o use case novos.

## Gateway, rede e Terraform

### Repositório `fiap-15soat-oficina-lambda-auth`

```
src/  test/  package.json  package-lock.json
terraform/
  versions.tf      backend S3: bucket fiap-15soat-oficina-tfstate, key lambda-auth/terraform.tfstate
  rede.tf          data sources: VPC, subnets privadas, RDS, NLB e listener
  segredos.tf      aws_secretsmanager_secret oficina/auth
  lambda.tf        função, security group, permissão de invocação pelo Gateway
  api_gateway.tf   API, stage, authorizer, VPC Link, integrações e rotas
  outputs.tf       api_gateway_url
.github/workflows/ci-cd.yml
README.md
```

### Descoberta de recursos de outros repositórios

Por data source, sem `terraform_remote_state`, no mesmo padrão do `infra-db`:

| Recurso | Filtro |
|---|---|
| VPC | tag `Name = ${project_name}-vpc` |
| Subnets privadas | tag `Name = ${project_name}-vpc-private-*` |
| RDS | `db_instance_identifier = oficina-db` |
| NLB | tag `kubernetes.io/service-name = oficina/oficina-api` |
| Listener | porta 80 do NLB acima |

### Lambda

- `nodejs22.x`, role `LabRole` (ARN montado com `aws_caller_identity`), 512 MB, timeout de 10 s.
- `vpc_config` nas subnets privadas, com security group só de egress. O RDS já libera 5432 para
  os CIDRs dessas subnets.
- Variáveis de ambiente: `DB_HOST`, `DB_NAME`, `DB_USER`, `SECRET_ID`. Nenhum segredo em variável.
- `iss` derivado de `https://${event.requestContext.domainName}`. Passar a URL do Gateway por
  variável criaria ciclo no Terraform (Lambda ↔ integração do Gateway).
- Secrets Manager alcançado pelo NAT gateway existente, sem VPC endpoint.

### Segredo `oficina/auth`

JSON `{jwtPrivateKey, dbPassword}`, com valores vindos de `TF_VAR_jwt_private_key` e
`TF_VAR_db_password`. Os valores ficam no state, que é criptografado no S3.

### API Gateway (HTTP API, stage `$default` com auto-deploy)

| Rota | Autorização | Integração | Throttling |
|---|---|---|---|
| `POST /auth/funcionario` | nenhuma | Lambda (payload 2.0) | 5 req/s, burst 10 |
| `POST /auth/cliente` | nenhuma | Lambda | 5 req/s, burst 10 |
| `POST /auth/cliente/primeiro-acesso` | nenhuma | Lambda | 5 req/s, burst 10 |
| `GET /.well-known/{proxy+}` | nenhuma | Lambda | padrão |
| `ANY /api/{proxy+}` | JWT authorizer | VPC Link → listener do NLB | padrão |
| `GET /aprovacao-os` | nenhuma | VPC Link → NLB | padrão |
| `GET /actuator/health` | nenhuma | VPC Link → NLB | padrão |
| `GET /swagger-ui.html`, `GET /swagger-ui/{proxy+}`, `GET /v3/api-docs`, `GET /v3/api-docs/{proxy+}` | nenhuma | VPC Link → NLB | padrão |

- **JWT authorizer:** `identity_sources = ["$request.header.Authorization"]`, `issuer` = endpoint da
  própria API e `audience = ["oficina-api"]`.
- **A AWS valida o discovery na criação do authorizer**, não apenas a cada requisição (confirmado
  em spike na conta real, 15/09/2026: sem a rota publicada a criação falha com *"Invalid issuer …
  must have a valid discovery endpoint"*). Por isso o authorizer declara `depends_on` da rota
  `GET /.well-known/{proxy+}`, do stage `$default` e da permissão de invocação da Lambda.
- **Ordem correta não basta:** no primeiro `apply` real o authorizer foi criado 1 segundo depois do
  stage e falhou com o mesmo erro — a rota leva alguns segundos para começar a responder. Um
  `time_sleep` de 60 s (provider `hashicorp/time`) entre a publicação e o authorizer resolve. Sem
  ele, o `apply` só passa na segunda execução, o que quebraria a pipeline num ambiente novo.
- `/actuator/prometheus` **não é roteado**: o `nri-bundle` lê de dentro do cluster. Isso fecha a
  exposição pública registrada no checklist.
- **VPC Link:** subnets privadas, com security group próprio. O NLB interno aceita o CIDR da VPC.

### Mudanças no repositório da API

- `k8s/service.yaml`: anotações `service.beta.kubernetes.io/aws-load-balancer-type: "nlb"` e
  `service.beta.kubernetes.io/aws-load-balancer-internal: "true"`. A troca de ELB clássico para NLB
  exige `kubectl delete service oficina-api -n oficina` uma vez antes do `apply`.
- `k8s/configmap.yaml`: `JWT_ISSUER: "<APP_PUBLIC_BASE_URL>"`, substituído pelo mesmo `sed` do
  workflow.
- GitHub Secret `APP_PUBLIC_BASE_URL` passa a ser a URL do Gateway (output `api_gateway_url`), o que
  corrige também os links do e-mail de aprovação.

### Ordem de subida (primeira vez)

1. `infra-k8s` (VPC, EKS, ECR)
2. `infra-db` (RDS)
3. Deploy da API: cria o NLB interno e a coluna `senha_hash`
4. `lambda-auth`: encontra o NLB e publica o Gateway
5. Atualizar `APP_PUBLIC_BASE_URL` com `api_gateway_url`
6. Novo deploy da API

A partir daí os deploys são independentes.

### Ordem de destruição

`lambda-auth` antes de remover o `Service` da API (o VPC Link referencia o listener do NLB), e antes
de `infra-db` e `infra-k8s`.

## CI/CD do `lambda-auth`

No mesmo padrão dos repositórios de infraestrutura:

| Evento | Etapas |
|---|---|
| Pull request | `npm ci`, `npm test` (unitários + integração com Postgres em service container), `terraform fmt -check`, `validate`, `plan` com o plano comentado no PR |
| Push para `main` | `npm ci --omit=dev`, zip de `src/` + `node_modules/`, `terraform apply` no Environment `producao`, smoke test |

**Smoke test:** `GET /.well-known/jwks.json` → 200 e `POST /auth/funcionario` com credencial
inválida → 401.

**Secrets:** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `DB_PASSWORD`,
`JWT_PRIVATE_KEY`.

`concurrency group` para não disputar o lock do DynamoDB.

## Riscos

| Risco | Mitigação |
|---|---|
| `LabRole` do Academy sem permissão para Secrets Manager, VPC Link ou Lambda em VPC | **Primeira tarefa do plano:** spike criando os três recursos manualmente antes de escrever o resto |
| NLB interno criado pelo controller in-tree sem regra de security group para o VPC Link | Verificar no spike; se faltar, anotar `aws-load-balancer-source-ranges` com o CIDR da VPC |
| Ativação de conta por terceiro que conhece CPF e e-mail | Risco aceito, registrado na RFC |
| Cold start da Lambda dentro da VPC na demonstração | Chamar `jwks.json` antes de gravar o vídeo |

## Fora do escopo

- Ambiente de homologação (decisão em aberto no checklist)
- Instrumentação da Lambda no New Relic
- Redefinição de senha do cliente
- Login de clientes PJ (CNPJ)
- Diagrama de sequência do fluxo de autenticação e RFC de estratégia de autenticação, que usam esta
  spec como base
