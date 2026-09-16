# RFC-0003: Estratégia de autenticação — CPF via Function Serverless (Lambda) + API Gateway

- **Status:** Aceito
- **Data:** 2026-09-15

## Resumo

A autenticação da aplicação passou a ser feita por uma Function Serverless
(`fiap-15soat-oficina-lambda-auth`, Node.js 22 na AWS Lambda) exposta por um AWS API
Gateway (HTTP API), que também é a porta de entrada única do sistema e roteia as chamadas
para a API principal via VPC Link. A API (`fiap-15soat-oficina-api`) deixou de emitir
tokens de sessão: ela só valida os JWT (RS256) emitidos pela Lambda, usando o mesmo par de
chaves RSA compartilhado via Secrets Manager.

## Motivação / Contexto

O desafio exige (`docs/requisitos-fase3.md`): um API Gateway para controle e roteamento,
proteção de rotas sensíveis via CPF, e uma function serverless que valide o CPF, consulte
existência/status do cliente na base e emita o JWT — além de decidir formalmente como a API
passa a confiar no token emitido por essa function.

Duas questões precisavam de decisão:

1. Como o login de funcionário (e-mail/senha, existente desde a Fase 2) e o login de
   cliente (CPF, novo nesta fase) convivem no mesmo sistema de autenticação.
2. Como a API relaxa a confiança num token assinado fora dela, sem reimplementar a lógica
   de emissão — as três opções cogitadas desde o início eram "mesmo par RSA via Secrets
   Manager, JWKS endpoint, ou Cognito".

## Decisão

### 1. Onde a autenticação acontece

A Lambda `oficina-auth` concentra os dois logins:

- `POST /auth/funcionario` — e-mail (`username`) + senha, contra as tabelas
  `users`/`roles`/`users_roles` já existentes; token de 8h com as roles do banco.
- `POST /auth/cliente` — CPF + senha, contra a coluna `cliente.senha_hash`; token de 1h
  com `roles = ["CLIENTE"]` e `sub` = id do cliente.
- `POST /auth/cliente/primeiro-acesso` — cliente ativo sem senha ainda define uma,
  conferindo CPF + e-mail do cadastro. A gravação é um único `UPDATE ... WHERE status =
  'ATIVO' AND email = :email AND senha_hash IS NULL`, atômico e portanto de uso único: não
  existe um segundo passo que possa ser repetido ou corrido.
- Erros de credencial não distinguem "cliente não existe" de "senha errada" (ambos caem em
  `401`), e usam um hash fictício de custo idêntico quando o cliente não é encontrado
  (`bcrypt.compare(senha, hash ?? HASH_FICTICIO)`) para não vazar, por tempo de resposta,
  se um CPF está cadastrado.

A API perde completamente o endpoint de login: `AuthController` mantém só
`POST /api/auth/register`, restrito a `ADMIN`, para cadastro interno de novos usuários
funcionários — a API nunca mais emite token de sessão.

Concentrar os dois logins na mesma Lambda, em vez de duas funções separadas, evita duplicar
a lógica de emissão de JWT e o JWKS; a Lambda já precisa acessar o mesmo RDS que a API para
o login de cliente, então reaproveitar a conexão para o login de funcionário não introduz
uma dependência nova.

### 2. Como a API passa a confiar no token

Decisão: **mesmo par RSA via Secrets Manager** — a mais simples das três opções
consideradas.

- `JWT_PRIVATE_KEY` é o mesmo segredo nos dois repositórios (`api` e `lambda-auth`).
- A API valida `aud = oficina-api` e `iss = <URL do API Gateway>` no próprio `JwtDecoder`
  (`SecurityConfig.sessaoJwtDecoder`), além da assinatura RSA — não basta uma assinatura
  válida, o token também precisa ter sido emitido pelo Gateway certo.
- O `iss` é resolvido dinamicamente pela Lambda a partir do domínio da própria requisição
  (`https://${event.requestContext.domainName}`), então o mesmo código funciona local e em
  produção sem hardcode de URL.
- A Lambda também expõe `/.well-known/openid-configuration` e `/.well-known/jwks.json` —
  não porque a API os consome (ela usa a chave pública fixa via Secrets Manager), mas
  porque o **authorizer JWT do próprio API Gateway** precisa deles para validar o token
  antes de rotear `/api/{proxy+}` para a API através do VPC Link. Ou seja, o token é
  validado duas vezes por caminhos independentes: uma vez pelo Gateway (usando o JWKS que a
  própria Lambda publica) e de novo pela API (usando a chave pública fixa via Secrets
  Manager).

### 3. Onde fica o roteamento

- Um único API Gateway HTTP API (`terraform/api_gateway.tf`, repositório `lambda-auth`) é
  a porta de entrada pública de todo o sistema:
  - `/auth/*` e `/.well-known/*` → integração Lambda direta (`AWS_PROXY`), sem authorizer.
  - `/api/{proxy+}` → integração HTTP (`HTTP_PROXY`) via VPC Link até o NLB interno da
    API, protegida pelo authorizer JWT.
  - Rotas públicas da API (Swagger, `/aprovacao-os`, `/actuator/health`) → mesma
    integração HTTP, sem authorizer.
- O `Service` Kubernetes da API deixou de ser um load balancer público: virou um NLB
  interno (`aws-load-balancer-internal: "true"`), alcançável só de dentro da VPC — a única
  forma de chegar nas rotas protegidas vindo de fora é pelo Gateway.
- Detalhe operacional: a AWS valida o `/.well-known/openid-configuration` **na criação** do
  authorizer, não só a cada requisição — por isso o Terraform precisa de um `depends_on`
  explícito garantindo que a rota de discovery, o stage e a permissão de invocação da
  Lambda já existam antes do authorizer (`e3c5d0e`).

## Alternativas consideradas

- **JWKS endpoint como única fonte de confiança** (sem par de chaves compartilhado) —
  descartado por ora: exigiria a API buscar e cachear o JWKS da Lambda em runtime (rotação
  de chave, TTL de cache, fallback se a Lambda cair), complexidade que o prazo do desafio
  não justifica. A Lambda já publica JWKS de qualquer forma, mas só para o authorizer do
  Gateway usar.
- **Amazon Cognito** — descartado: adicionaria um serviço gerenciado extra (User Pool) para
  replicar uma lógica de negócio específica (CPF como identificador de cliente, funcionário
  e cliente com esquemas de credencial diferentes) que o Cognito não modela nativamente sem
  customização via Lambda triggers — acabando na mesma quantidade de código Lambda que a
  solução escolhida, com a complexidade adicional de administrar o User Pool.
- **Duas Lambdas separadas** (uma para funcionário, outra para cliente) — descartado:
  dobraria o número de segredos, permissões IAM e `terraform apply`, sem ganho de
  isolamento real, já que as duas compartilham o mesmo RDS e o mesmo par de chaves.

## Consequências

- A API nunca deve reintroduzir um endpoint de login — se precisar, é sinal de que esta
  decisão foi revertida sem atualizar este documento.
- Rotação da chave RSA privada precisa acontecer nos dois repositórios ao mesmo tempo
  (`api` e `lambda-auth`), senão a API passa a rejeitar todos os tokens novos.
- O `iss` dinâmico significa que qualquer mudança de domínio do API Gateway (ex.: domínio
  customizado) exige também atualizar `JWT_ISSUER`/`APP_PUBLIC_BASE_URL` na API, senão os
  tokens emitidos deixam de validar.
- Diagrama de sequência do fluxo completo em [`docs/sequencia-autenticacao.md`](../sequencia-autenticacao.md).
