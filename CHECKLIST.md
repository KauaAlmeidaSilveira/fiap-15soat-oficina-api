# Checklist de Entrega — Tech Challenge Fase 3

> Estado levantado em 13/09/2026, atualizado em 14/09/2026 (pós-separação dos repositórios).
> Legenda: `[ ]` pendente · `[x]` concluído · `[?]` não verificável localmente (falta ferramenta/credencial, não falta de trabalho). Sem status "parcial": um item composto por várias partes vira uma linha por parte, cada uma binária.

**Atualização de 14/09/2026 — Seção 3 (observabilidade) commitada em `14a1e8c`**, 37 arquivos, verificada por `mvn verify` (178 testes, 0 falhas), `terraform validate`, `terraform plan` contra a conta real do New Relic (`10 to add`) e execução das 9 queries NRQL via NerdGraph. Falta apenas rodar contra infraestrutura no ar.

**Atualização de 14/09/2026 — repositório `fiap-15soat-oficina-lambda-auth` criado.** Confirmado via GitHub API (`api.github.com/repos/KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth`, HTTP 200): repositório público, conta `KauaAlmeidaSilveira` (a mesma da aplicação, não a de infra), criado em 15/09/2026 00:44 UTC. **Está vazio** — `size: 0` e a API de commits responde "Git Repository is empty" (nenhum commit, nem README). Não há hash para referenciar ainda.

**Atualização de 14/09/2026 — Seção 4 (documentação) fechada, exceto o que depende do RFC de autenticação.** Adicionados `docs/adr/` (2 ADRs, só de decisões já implementadas), `docs/rfc/` (2 RFCs, um absorvendo a justificativa formal do banco), `docs/sequencia-abertura-os.md` (Mermaid), `docs/requisitos-fase3.md` (espelho do PDF do desafio) e `docs/diagrama-infraestrutura.png` redesenhado com a 4ª coluna de Observabilidade (New Relic). Critério adotado: só documentar o que já está implementado — nada de ADR/RFC/diagrama para API Gateway ou Lambda enquanto isso não existir de fato no código; o conteúdo da nova coluna do diagrama foi conferido contra o código real (`k8s/newrelic-values.yaml`, `infra/newrelic.tf`, `CorrelationIdFilter`) em duas rodadas de correção. Ressalva conhecida: a seta de métricas do diagrama sai visualmente perto do metrics-server/HPA em vez de sair dos Pods — o rótulo está correto, só o ponto de saída não reflete o mecanismo exato; aceito como está.

**Atualização de 15/09/2026 — observabilidade validada contra infraestrutura viva** (commits `a4a3ea7`, `b3d80ee`, `c87dfe4`, mesclados na `master` em paralelo a esta sessão de documentação). Bucket S3 + tabela DynamoDB do backend Terraform criados (conta `647776094260`) e secrets cadastrados nos repos de infra — os dois itens operacionais que faltavam na Seção 2. Na Seção 3: `terraform apply` real dos dashboards/alertas, `nri-bundle` rodando via Helm, nomes de métrica confirmados idênticos às queries, e `trace.id` chegando ao MDC dos logs (exigiu adicionar `com.newrelic.logging:logback`, já que o agente sozinho não injeta). De quebra, dois bugs reais corrigidos: o painel de tempo médio por status misturava códigos HTTP, e JSON malformado devolvia 500 em vez de 400. A exposição pública do `/actuator/prometheus` foi resolvida depois, como efeito colateral do trabalho de API Gateway (ver ponto de atenção 7).

**Atualização de 15/09/2026 — `lambda-auth` deixou de estar vazio: código completo commitado** (`0b89745`..`d6764c4`). CPF (validação e dígito verificador), autenticação de funcionário e de cliente com bcrypt, emissão de JWT RS256 via `jose`, repositório Postgres, handler HTTP com rotas `/auth/*` e descoberta OIDC/JWKS, Terraform completo (Lambda + API Gateway HTTP API + VPC Link + Secrets Manager) e pipeline de CI/CD (testes + `terraform plan`/`apply` + smoke test) — mesmo padrão dos outros dois repos de infra. Verificado agora nesta máquina: `npm test` → 36 testes, 0 falhas; `terraform fmt -check` e `terraform validate` → OK. `npm run test:integracao` (Postgres real) não verificável — Docker instalado mas não em execução [?]. Isso resolve, no nível de código, quase todo o item "Function Serverless (Lambda)" da Seção 1 (CPF, consulta de status, emissão de JWT) e a decisão de confiança do token (mesmo par RSA via Secrets Manager). Duas lacunas de integração ficam expostas por essa mudança, ainda não corrigidas em nenhum repo — viraram os pontos de atenção 8 e 9: (1) o Terraform do `lambda-auth` só encontra a API por um NLB tagueado pelo `Service` do K8s, mas esse `Service` continua um `LoadBalancer` genérico sem AWS Load Balancer Controller em `infra-k8s`, o que no EKS produz um Classic Load Balancer por padrão — nem o data source nem o VPC Link do API Gateway o aceitam; (2) o schema que o `lambda-auth` espera tem a coluna `cliente.senha_hash`, que a entidade `Cliente` do repo `api` ainda não tem.

**Atualização de 15/09/2026 — `infra-k8s`: fix técnico, não muda itens do checklist.** Commit `71a5922` substitui o módulo `terraform-aws-modules/eks` por recursos nativos do provider (`aws_eks_cluster`/`aws_eks_node_group`/`aws_eks_addon`), contornando `iam:GetRole` implícito no módulo sob as restrições do AWS Academy.

**Atualização de 15/09/2026 — `api` fecha os dois gaps de integração dos pontos de atenção 8 e 9, e a troca de eixo da autenticação (ponto 2) foi feita.** Merge `6a118e6` (`feat/api-gateway-auth`, 8 commits): `k8s/service.yaml` ganhou as anotações `aws-load-balancer-type: nlb` + `aws-load-balancer-internal: true` (mais um step idempotente no `ci-cd.yml` que apaga o `Service` uma vez, já que um Classic ELB não vira NLB in-place) — resolve o ponto 8; `Cliente.java` ganhou `@Column(name = "senha_hash", length = 60)` — resolve o ponto 9. O login antigo (`AuthController`) perdeu `/login`, só resta `/register` (ADMIN); `SecurityConfig` agora restringe `/api/minhas-ordens-servico/**` a `ROLE_CLIENTE` e o resto de `/api/**` a `ADMIN`/`RECEPCAO`/`OPERADOR`, e o `JwtDecoder` passou a validar `aud = oficina-api` e `iss` (`jwt.issuer`, injetado como o mesmo valor de `APP_PUBLIC_BASE_URL`/`JWT_ISSUER` no `configmap.yaml`). Endpoint novo `GET /api/minhas-ordens-servico(/{id})` lê o `sub` do JWT como `clienteId`, batendo com o token que o `lambda-auth` emite para cliente. README documenta a troca (tabela funcionário/cliente) dos dois lados agora, mas ainda sem RFC/ADR formal. `lambda-auth` também recebeu 2 commits (`e3c5d0e`): corrige ordem de criação do JWT authorizer vs. rota de discovery, e passa a rodar CI/CD em push para `master`.

**Atualização de 15/09/2026 (noite) — autenticação e API Gateway no ar.** Lambda `oficina-auth` e API Gateway HTTP criados por Terraform no repositório `fiap-15soat-oficina-lambda-auth` (25 recursos), API migrada para NLB interno e fluxo completo validado contra a infraestrutura viva. Isso fecha a Seção 1 inteira e também a **única decisão que estava em aberto na Seção 3**: `/actuator/prometheus` não tem rota no Gateway e a API não é mais pública, então a métrica só é lida de dentro do cluster. Pendências operacionais: os secrets do `lambda-auth` no GitHub (exigem admin no repositório) e as credenciais do Academy vencidas nos repositórios, que deixam as pipelines vermelhas.

**Resumo original:** a Fase 3 é majoritariamente trabalho novo. O que a Fase 2 entregou (EKS + RDS + Terraform + HPA + CI/CD) cobre parte da "Infraestrutura obrigatória", mas **autenticação por CPF, API Gateway, Lambda, observabilidade, a separação em 4 repositórios e toda a documentação arquitetural (sequência, RFCs, ADRs, ER) não existem hoje**.

---

## 1. Autenticação e API Gateway

**Entregue e no ar em 15/09/2026:** `https://vhb3qkxdq8.execute-api.us-east-1.amazonaws.com`

- [x] **API Gateway** — AWS API Gateway HTTP API. `/auth/*` e `/.well-known/*` vão para a Lambda; `/api/{proxy+}` passa pelo JWT authorizer e segue por VPC Link até um **NLB interno**. O `Service` deixou de ser `LoadBalancer` público (`k8s/service.yaml`), então a API só é alcançável pelo Gateway
- [x] **Proteger rotas sensíveis com autenticação via CPF** — cliente entra com **CPF + senha** (`POST /auth/cliente`), com a senha criada em `POST /auth/cliente/primeiro-acesso` conferindo o e-mail do cadastro. CPF sozinho não autentica: é dado quase público. O login de funcionário (e-mail + senha) também migrou para a Lambda
- [x] **Function Serverless (Lambda)** — `oficina-auth`, Node 22, subnets privadas, repositório `fiap-15soat-oficina-lambda-auth`:
  - [x] Validar o CPF do cliente — `src/cpf.js`, dígitos verificadores próprios; CNPJ responde 400
  - [x] Consultar **existência e status** do cliente na base — `src/repositorio.js` lê `cliente` no RDS; `INATIVO` responde 403, e só depois de a senha conferir
  - [x] Gerar e devolver JWT válido — RS256 com `aud = oficina-api` e `iss` = URL do Gateway; 8 h para funcionário, 1 h para cliente
- [x] Decidir e documentar como a API passa a confiar no token emitido pela Lambda — **mesmo par RSA via Secrets Manager**. A Lambda assina com a chave privada do segredo `oficina/auth`; a API valida assinatura, `aud` e `iss`, e o Gateway valida de novo pelo JWKS que a própria Lambda publica. Formalizado em [RFC-0003](docs/rfc/0003-estrategia-de-autenticacao-cpf-via-lambda.md)
- [x] Rotas de leitura do cliente na API — `GET /api/minhas-ordens-servico` e `/{id}`, com o `clienteId` vindo do `sub` do token
- [x] **Falha de segurança corrigida no caminho:** o token do link de aprovação por e-mail (validade de 7 dias, sem `aud`) era aceito como token de sessão e dava acesso de leitura a todos os clientes. Agora os dois tokens se excluem
- [x] Validado ponta a ponta contra a infraestrutura no ar: primeiro acesso (201), repetição (422), CPF inválido (400), login por CPF (200), "minhas OS" (200), cliente em rota de funcionário (403), sem token (401), senha errada (401)

## 2. Estrutura de Repositórios e CI/CD

### Os 4 repositórios separados

Nomenclatura definida, mantendo o prefixo já existente:

| # | Repositório | Papel | Situação |
|---|---|---|---|
| 1 | `fiap-15soat-oficina-api` | Aplicação Java, manifestos K8s, observabilidade | **criado no GitHub** (`KauaAlmeidaSilveira`) existe, **reorganizado** |
| 2 | `fiap-15soat-oficina-infra-k8s` | VPC, EKS, ECR | **no GitHub** (`JulioNCavalcanti`), commit `a8cceba` |
| 3 | `fiap-15soat-oficina-infra-db` | RDS PostgreSQL | **no GitHub** (`JulioNCavalcanti`), commit `d4f52e9` |
| 4 | `fiap-15soat-oficina-lambda-auth` | Lambda de autenticação por CPF + API Gateway | **código completo no GitHub** (`KauaAlmeidaSilveira`), commit `e3c5d0e` — 36 testes unitários passando |

- [x] **Definir nomenclatura** — prefixo `fiap-15soat-oficina-` preservado; o repo atual não é renomeado
- [x] **`infra-k8s` montado e validado** — `vpc.tf`, `eks.tf`, `ecr.tf` copiados; `versions.tf` sem o provider New Relic; 3 outputs de rede novos (`vpc_id`, `private_subnets`, `private_subnet_cidrs`). `terraform validate` e `fmt` OK, zero variável obrigatória
- [x] **`infra-db` montado e validado** — `rds.tf` desacoplado do EKS e `data.tf` novo. `terraform validate` e `fmt` OK, só `db_password` obrigatória
- [x] **Acoplamento RDS → EKS quebrado** — `rds.tf` referenciava `module.eks.node_security_group_id`, a única aresta que impedia separar os dois repos de infraestrutura. Trocado por liberação via CIDR das subnets privadas, descobertas por data source
- [x] **Repo da aplicação reorganizado** — `infra/` removido, `observability/` criado com o Terraform do New Relic, `.gitignore` generalizado, referências órfãs corrigidas em `README.md`, `k8s/README.md`, `CLAUDE.md` e nos comentários dos manifestos
- [x] **`infra-k8s` e `infra-db` criados no GitHub e pushados** — `git init` em `main`, commit inicial, remote ligado. Local e remoto conferidos no mesmo hash, sem divergência
- [x] **README de `infra-k8s` e `infra-db`** — propósito, tecnologias, passos, diagrama da arquitetura e tabela de outputs. Documentam também as restrições do AWS Academy e a ordem de destruição (banco antes da VPC)
- [x] **Criar o repositório `lambda-auth`** — último dos quatro, criado em `KauaAlmeidaSilveira` (mesma conta da aplicação). Deixou de estar vazio: código completo (Lambda + Terraform + CI/CD + testes), commits `0b89745`..`e3c5d0e` — ver Seção 1
- [x] **Criar o bucket S3 `fiap-15soat-oficina-tfstate` e a tabela DynamoDB `fiap-15soat-oficina-tflock`** (conta `647776094260`, 15/09/2026) — os três `versions.tf` já apontam para eles
- [x] **Pipeline de CI/CD nos dois repos de infra** — `.github/workflows/terraform.yml` em cada um (`8992338` e `76767b3`): `fmt`/`validate`/`plan` no PR com o plano comentado no próprio PR, `apply` no push para `main`. Validação roda sem backend, então sobrevive a credencial expirada; `apply` atrelado ao Environment `producao`; `concurrency group` evita disputa pelo lock do DynamoDB; no `infra-db` a senha entra como `TF_VAR_db_password`, sem virar arquivo no runner
- [x] Cadastrar os secrets nos dois repos: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` e, no `infra-db`, `DB_PASSWORD`
- [x] Pipeline de CI/CD do `lambda-auth` — `.github/workflows/ci-cd.yml` (`d6764c4`): testes unitários e de integração + `terraform fmt`/`validate`/`plan` (plano comentado no PR) em pull request; empacotamento da Lambda + `terraform apply` no Environment `producao` + smoke test no push para `main`, mesmo padrão dos outros dois repos de infra
- [x] `.terraform.lock.hcl` do `observability/` gerado e commitado em `fbb1649` — trava só o provider `newrelic`
- [x] Esqueleto do `lambda-auth` — superado: não é mais só esqueleto, é implementação completa (ver Seção 1)
- [x] `api` — `soat-architecture` confirmado como colaborador (evidência: `settings/access`, 15/09/2026)
- [x] `lambda-auth` — convite enviado a `soat-architecture` (evidência: `settings/access`, 15/09/2026). Aceite depende do outro lado; nossa parte está feita
- [x] `infra-k8s` — convite enviado a `soat-architecture` (evidência: `settings/access`, 15/09/2026). Aceite depende do outro lado; nossa parte está feita
- [x] `infra-db` — convite enviado a `soat-architecture` (evidência: `settings/access`, 15/09/2026). Aceite depende do outro lado; nossa parte está feita

### Regras de proteção

- [ ] **Branch `main`/`master` protegida (sem commits diretos)** — ainda não foi feito. O histórico mostra commits direto no `master` (`98c5386`, `8441082`, `ea95f99`…) e **apenas 1 PR** em todo o projeto (`#1`)
- [ ] **Uso obrigatório de Pull Requests para merge** — ainda não foi feito

### Infraestrutura obrigatória

- [x] API Gateway para controle e roteamento — Terraform completo em `lambda-auth` (ver Seção 1)
- [x] Function Serverless para autenticação — código e testes completos em `lambda-auth` (ver Seção 1)
- [x] Banco de Dados Gerenciado — RDS PostgreSQL (`infra/rds.tf`)
- [x] Cluster Kubernetes com escalabilidade — EKS (`infra/eks.tf`) + HPA (`k8s/hpa.yaml`, 1→2 réplicas, 70% CPU)
- [x] Terraform para provisionamento — `infra/` (mas ver separação em repositórios acima)

## 3. Monitoramento e Observabilidade

**Ferramenta: New Relic**, conta free região US, account `7428180`. Código commitado em `14a1e8c`.
Design em `docs/superpowers/specs/2026-09-13-observabilidade-newrelic-design.md`.

### Implementado e verificado

- [x] Escolher e integrar a ferramenta
- [x] **Latência das APIs** — agente Java 9.4.0 em estágio próprio do `Dockerfile`, ativado pelo entrypoint só quando há `NEW_RELIC_LICENSE_KEY`
- [x] **Alertas para falhas no processamento de OS** — `MetricasGateway.falhaDeIntegracao` instrumentado no SMTP e na notificação de aprovação; condições em `infra/newrelic_alertas.tf`
- [x] **Logs estruturados em JSON com correlação** — `logback-spring.xml` (JSON no perfil `default`, texto em `dev`/`test`) e `CorrelationIdFilter` com header `X-Correlation-Id`
- [x] **Dashboards como código** — `infra/newrelic.tf`, duas páginas cobrindo volume diário, tempo médio por status, transições, falhas de integração, latência, taxa de erro e endpoints mais lentos
- [x] **Tempo médio por status** viabilizado pelo campo novo `statusAlteradoEm` — sem ele não havia como medir `EM_DIAGNOSTICO` nem `AGUARDANDO_APROVACAO`
- [x] Expor métricas — `micrometer-registry-prometheus` em `/actuator/prometheus`, com anotações de scrape no `deployment.yaml`
- [x] `nri-bundle` configurado em `k8s/newrelic-values.yaml` (Pixie desligado, `lowDataMode` ligado — o node group é pequeno)
- [x] Desligar `show-sql` em produção — inflava o volume de log sem necessidade
- [x] Plumbing dos segredos — `secret.yaml.example`, `deployment.yaml`, workflow, `docker-compose.yml`, `.env.example`

### Validações já feitas

- [x] `mvn verify` — 178 testes, 0 falhas, gate JaCoCo aprovado
- [x] `terraform validate` e `terraform fmt` nos arquivos novos
- [x] `terraform plan` autenticando na conta real — `10 to add, 0 to change, 0 to destroy`
- [x] As 9 queries NRQL executadas via NerdGraph: todas sintaticamente válidas
- [x] URL e estrutura do agente conferidas contra o servidor de download (9.4.0 é a mais recente)
- [x] User API key (`NRAK-`) validada — autentica e enxerga a conta `7428180`

### Validação com a infraestrutura no ar (15/09/2026)

| # | O quê | Estado |
|---|---|---|
| 1 | `alertas_email` em `observability/terraform.tfvars` | ✅ |
| 2 | `terraform apply` dos dashboards e alertas | ✅ 9 recursos na conta `7428180` |
| 3 | `NEW_RELIC_LICENSE_KEY` nos GitHub Secrets | ✅ agente conectado, `appName = oficina-api` |
| 4 | Nomes das métricas batem com as queries | ✅ `oficina_os_*` chegam idênticos; OS de teste 5 e 6 |
| 5 | `nri-bundle` via Helm | ✅ 9 pods, métricas Prometheus, K8s e logs chegando |
| 6 | `trace.id` do agente no JSON | ✅ `NewRelic:trace.id`/`span.id` via `NewRelicAsyncAppender` (`c87dfe4`) |
| 7 | Fechar a exposição pública de `/actuator/prometheus` | ✅ resolvido em código (`450ccba`) — `Service` virou NLB interno, ver ponto de atenção 7 |

Correções que a validação exigiu:

- [x] Painel "tempo médio por status" agrupava também códigos HTTP (`FACET status` colidia com `http_server_requests`) — filtrado por `metricName` (`a4a3ea7`)
- [x] `trace.id` não chegava ao MDC: o agente sozinho não injeta — adicionado `com.newrelic.logging:logback` (`c87dfe4`)
- [x] JSON malformado devolvia 500 — `HttpMessageNotReadableException` agora vira 400 (`b3d80ee`)

Comportamentos esperados, não defeitos:

- O `nri-prometheus` converte counters em delta: o primeiro valor de cada série se perde (actuator mostra 2 OS, New Relic mostra 1)
- Queries com `SINCE 1 day ago` voltam vazias nas primeiras horas de dados; janelas de 1h/3h já respondem

## 4. Documentação da Arquitetura

- [x] **Diagrama de Componentes com visão de nuvem, APIs, banco e monitoramento** — `docs/diagrama-infraestrutura.png` redesenhado com uma 4ª coluna de Observabilidade (New Relic: nri-bundle, agente APM, logs, dashboards, alertas), mantendo as 3 colunas da Fase 2 (Pipeline CI/CD, Infraestrutura AWS, Runtime Kubernetes). API Gateway e Lambda não entram — não existe código para eles ainda. `docs/diagrama-componentes.png` (Clean Architecture, camadas de código) não muda, não é o que este item do desafio pede
- [x] **Diagrama de Sequência** para o fluxo de **autenticação** (CPF → API Gateway → Lambda → JWT → API) — [`docs/sequencia-autenticacao.md`](docs/sequencia-autenticacao.md), Mermaid, cobre login de cliente por CPF e o consumo de uma rota protegida (`GET /api/minhas-ordens-servico`), com notas para o login de funcionário e o primeiro acesso
- [x] **Diagrama de Sequência** para o fluxo de **abertura de ordem de serviço** — `docs/sequencia-abertura-os.md`, Mermaid, cobre a criação da OS (`POST /api/ordens-servico`) conforme a redação literal do PDF do desafio
- [x] **RFCs** para decisões técnicas relevantes — `docs/rfc/`: [0001 escolha da nuvem (AWS)](docs/rfc/0001-escolha-da-nuvem-aws.md), [0002 escolha do banco (RDS PostgreSQL)](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md), [0003 estratégia de autenticação (CPF via Lambda)](docs/rfc/0003-estrategia-de-autenticacao-cpf-via-lambda.md)
- [x] **ADRs** para decisões arquiteturais permanentes — `docs/adr/`: [0001 uso de HPA](docs/adr/0001-uso-de-hpa-para-escalabilidade.md), [0002 comunicação síncrona via REST](docs/adr/0002-comunicacao-sincrona-via-rest.md)
- [x] **Justificativa formal da escolha do banco de dados** — absorvida no [RFC-0002](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md), evita duplicar o mesmo conteúdo em dois documentos
- [x] **Ajustes no modelo relacional + diagrama ER + explicação dos relacionamentos** — campo `status` do cliente já existe (PR #2); `docs/diagrama-er.png` gerado via Mermaid a partir de `docs/diagrama-er.mmd` (texto, versionável), conferido campo a campo contra as 10 entidades reais; explicação escrita em [`docs/modelo-relacional.md`](docs/modelo-relacional.md)
- [x] Endereçar "consistência e performance" do banco — documentado no [RFC-0002](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md): FKs obrigatórias, restrições de unicidade, fronteira transacional por gateway, `FetchType.LAZY`

## 5. Entregáveis

### Repositórios Git

- [x] 4 repositórios separados com código, CI/CD e instruções claras no `README.md` — confirmado nos quatro agora que `lambda-auth` saiu do vazio (ver Seção 2)
- [x] Dockerfiles em cada repositório (quando aplicável) — só `api` roda em container (`Dockerfile` na raiz); `infra-k8s`/`infra-db` são só Terraform e `lambda-auth` empacota `.zip` (`scripts/empacotar.sh`) — Docker não se aplica a nenhum dos três
- [x] Pipelines de CI/CD funcionais em cada repositório — `api` (`ci-cd.yml`), `infra-k8s` e `infra-db` (`terraform.yml`), `lambda-auth` (`ci-cd.yml`, novo)
- [ ] Links para os deploys ativos

### README.md de cada repositório

Cada critério (propósito, tecnologias, passos, diagrama) é checado por repositório — um agregado tipo "2 de 4" escondia qual repo falta o quê.

- [x] `infra-k8s` — propósito, tecnologias, passos de execução e diagrama da arquitetura presentes no README (confirmado na linha de "README de infra-k8s e infra-db" acima)
- [x] `infra-db` — propósito, tecnologias, passos de execução e diagrama da arquitetura presentes no README (idem)
- [x] `api` — README tem "Sobre o Projeto" (propósito), "Stack tecnológica", "Como executar" e os dois diagramas embutidos (`diagrama-componentes.png`, `diagrama-infraestrutura.png`). Ressalva: os diagramas são os mesmos da Fase 2 e ainda não mostram API Gateway/Lambda/observabilidade — acompanha o item da Seção 4 acima, não bloqueia este checkbox
- [x] `lambda-auth` — README tem arquitetura (diagrama Mermaid), seção de autenticação com o contrato das rotas, "Tecnologias" e passos de "Desenvolvimento"/"Deploy"
- [x] Link para o Swagger/Postman das APIs — `postman/oficina-api.collection.json` + `.environment.json` criados, cruzados contra os controllers reais (2 bugs corrigidos: corpo faltando em "Aprovar OS", variável errada em "Remove Item à OS") e completados com o endpoint novo do PR #2 (`PATCH /clientes/{id}/status`) e o de aprovação pública que o README já prometia

### Vídeo de demonstração (YouTube/Vimeo, até 15 minutos)

- [ ] Execução da pipeline CI/CD
- [ ] Deploy automatizado
- [ ] Dashboard de monitoramento com análise ao vivo
- [ ] Consumo das APIs protegidas
- [ ] Autenticação com CPF
- [ ] Logs e traces em execução

### Entrega no Portal do Aluno

- [ ] PDF único com links dos 4 repositórios
- [ ] Link do vídeo (até 15 minutos)
- [ ] Links das documentações
- [x] Confirmação do usuário `soat-architecture` adicionado a **todos** os repositórios — feito nos 4 (`api` confirmado; `lambda-auth`, `infra-k8s`, `infra-db` com convite enviado, aceite depende do outro lado) — ver Seção 2

---

## Pontos de atenção que atravessam as frentes

1. ✅ **RESOLVIDO (PR #2, antes desta sessão)** — ~~Modelagem do cliente: o `status` exigido pela Lambda não existe~~: `Cliente.status` (`ATIVO`/`INATIVO`) já existe em domínio, entidade JPA, mappers e diagrama ER (ver Seção 1 e Seção 4). Ponto de atenção mantido desatualizado por descuido em revisões anteriores deste arquivo.
2. ✅ **RESOLVIDO em 15/09/2026** (`cd24146`) — ~~Autenticação muda de eixo~~: `AuthController` perdeu `/login` (só resta `/register`, ADMIN); `SecurityConfig` restringe por role (`ROLE_CLIENTE` em `/api/minhas-ordens-servico/**`, staff no resto de `/api/**`) e valida `aud`/`iss`; `DataLoader` ganhou um CPF com dígito verificador válido (`52998224725`) para o cliente semeado; testes novos cobrem o cenário (`AutorizacaoPorPerfilIT`, `SessaoJwtDecoderUnitTest`, `MinhasOrdensServicoControllerIT`).
4. **Gate de cobertura JaCoCo (80%)** continua valendo em `core.usecase`, `core.domain.entity` e `entrypoint.controller` — código novo nesses pacotes sem teste quebra o `mvn verify` da CI.
5. **State do Terraform é local e não existe nesta máquina** — impede tanto retomar a infra atual com segurança quanto atender ao requisito de deploy automático em 4 repositórios. Ver Seção 2.
6. **Credenciais AWS são temporárias (Academy)** — o workflow usa `AWS_SESSION_TOKEN`. Toda retomada começa por renovar as credenciais locais **e** os três GitHub Secrets, senão o pipeline falha no deploy.
7. ✅ **RESOLVIDO em 15/09/2026** (`450ccba`, efeito colateral do ponto 8) — ~~`/actuator/prometheus` está público~~: o `Service` virou um NLB **interno** (`aws-load-balancer-internal: "true"`), então deixa de ser alcançável pela internet; o `nri-bundle` já lia as métricas de dentro do cluster (via anotação de scrape nos Pods), não pela porta pública, então a observabilidade não depende disso. `SecurityConfig` continua com `permitAll` na rota, mas isso deixa de importar fora da VPC.
8. ✅ **RESOLVIDO em 15/09/2026** (`450ccba`) — ~~NLB vs Classic Load Balancer entre `api` e `lambda-auth`~~: `k8s/service.yaml` ganhou as anotações `service.beta.kubernetes.io/aws-load-balancer-type: "nlb"` e `-internal: "true"`; como um Classic ELB não vira NLB in-place, o `ci-cd.yml` ganhou um step idempotente que apaga o `Service` uma única vez para forçar a recriação.
9. ✅ **RESOLVIDO em 15/09/2026** (`b9ec0e8`) — ~~Coluna `cliente.senha_hash` não existe na API~~: `Cliente.java` ganhou `@Column(name = "senha_hash", length = 60)`; com `ddl-auto=update`, a coluna é criada no próximo boot contra o RDS real.

---

## Higiene do repositório

- [x] Chaves JWT ignoradas em qualquer diretório — a regra antiga só cobria `src/main/resources/`, e as chaves geradas na raiz escapavam. Confirmado que nenhuma chave chegou a ser rastreada em commit algum.
- [x] Corrigir a referência à pasta `postman/` no `README.md` — pasta criada com collection + environment reais; a referência já estava correta, só faltavam os arquivos.
- [x] `.gitignore` do Terraform generalizado — os padrões eram `infra/terraform.tfvars`, `infra/.terraform/`; agora casam em qualquer diretório, mesma correção aplicada às chaves JWT.
- [x] O `CHECKLIST.md` da Fase 2 (que referenciava `infra/eks.tf`, `infra/rds.tf` etc., já removidos deste repo) foi substituído por este arquivo na renomeação — não existe mais como arquivo separado. Conteúdo antigo continua recuperável via git (`git show 98c5386:CHECKLIST.md`), nada commitado foi perdido.
