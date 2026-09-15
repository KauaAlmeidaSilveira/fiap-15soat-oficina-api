# Checklist de Entrega — Tech Challenge Fase 3

> Estado levantado em 13/09/2026, atualizado em 14/09/2026 (pós-separação dos repositórios).
> Legenda: `[ ]` pendente · `[x]` concluído · `[?]` não verificável localmente (falta ferramenta/credencial, não falta de trabalho). Sem status "parcial": um item composto por várias partes vira uma linha por parte, cada uma binária.

**Atualização de 14/09/2026 — Seção 3 (observabilidade) commitada em `14a1e8c`**, 37 arquivos, verificada por `mvn verify` (178 testes, 0 falhas), `terraform validate`, `terraform plan` contra a conta real do New Relic (`10 to add`) e execução das 9 queries NRQL via NerdGraph. Falta apenas rodar contra infraestrutura no ar.

**Atualização de 14/09/2026 — repositório `fiap-15soat-oficina-lambda-auth` criado.** Confirmado via GitHub API (`api.github.com/repos/KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth`, HTTP 200): repositório público, conta `KauaAlmeidaSilveira` (a mesma da aplicação, não a de infra), criado em 15/09/2026 00:44 UTC. **Está vazio** — `size: 0` e a API de commits responde "Git Repository is empty" (nenhum commit, nem README). Não há hash para referenciar ainda.

**Atualização de 14/09/2026 — Seção 4 (documentação) fechada, exceto o que depende do RFC de autenticação.** Adicionados `docs/adr/` (2 ADRs, só de decisões já implementadas), `docs/rfc/` (2 RFCs, um absorvendo a justificativa formal do banco), `docs/sequencia-abertura-os.md` (Mermaid), `docs/requisitos-fase3.md` (espelho do PDF do desafio) e `docs/diagrama-infraestrutura.png` redesenhado com a 4ª coluna de Observabilidade (New Relic). Critério adotado: só documentar o que já está implementado — nada de ADR/RFC/diagrama para API Gateway ou Lambda enquanto isso não existir de fato no código; o conteúdo da nova coluna do diagrama foi conferido contra o código real (`k8s/newrelic-values.yaml`, `infra/newrelic.tf`, `CorrelationIdFilter`) em duas rodadas de correção. Ressalva conhecida: a seta de métricas do diagrama sai visualmente perto do metrics-server/HPA em vez de sair dos Pods — o rótulo está correto, só o ponto de saída não reflete o mecanismo exato; aceito como está.

**Resumo original:** a Fase 3 é majoritariamente trabalho novo. O que a Fase 2 entregou (EKS + RDS + Terraform + HPA + CI/CD) cobre parte da "Infraestrutura obrigatória", mas **autenticação por CPF, API Gateway, Lambda, observabilidade, a separação em 4 repositórios e toda a documentação arquitetural (sequência, RFCs, ADRs, ER) não existem hoje**.

---

## 1. Autenticação e API Gateway

- [ ] **API Gateway** (AWS API Gateway, Kong, Traefik ou outro) para controle e roteamento — nada no repositório hoje; o `Service` do K8s é um `LoadBalancer` direto (`k8s/service.yaml`)
- [ ] **Proteger rotas sensíveis com autenticação via CPF** — hoje o login é `username` (e-mail) + senha em `AuthUseCase.login()` e `POST /api/auth/login` (`dto/request/LoginRequest.java`)
- [ ] **Function Serverless (Lambda)** — repositório/código inexistente. Deve:
  - [ ] Validar o CPF do cliente — há lógica reaproveitável em `src/main/java/br/com/fiap/oficina/validation/CpfOuCnpjValidator.java`
  - [x] Consultar **existência e status** do cliente na base — desbloqueado: `Cliente.status` (`ATIVO`/`INATIVO`) mesclado na `master` via PR #2 (`feat/status-cliente`), 195 testes passando. Falta só a Lambda em si consumir isso
  - [ ] Gerar e devolver JWT válido para as APIs protegidas — o par RSA e a emissão já existem (`config/SecurityConfig.java` → `NimbusJwtEncoder`, `dataprovider/token/TokenAutenticacaoGatewayImpl.java`); a Lambda precisa assinar com chave que a API confie (hoje as chaves vêm do Secret do K8s)
- [ ] Decidir e documentar como a API passa a confiar no token emitido pela Lambda (mesmo par RSA via Secrets Manager, JWKS endpoint, ou Cognito)

## 2. Estrutura de Repositórios e CI/CD

### Os 4 repositórios separados

Nomenclatura definida, mantendo o prefixo já existente:

| # | Repositório | Papel | Situação |
|---|---|---|---|
| 1 | `fiap-15soat-oficina-api` | Aplicação Java, manifestos K8s, observabilidade | existe, **reorganizado** |
| 2 | `fiap-15soat-oficina-infra-k8s` | VPC, EKS, ECR | **no GitHub** (`JulioNCavalcanti`), commit `a8cceba` |
| 3 | `fiap-15soat-oficina-infra-db` | RDS PostgreSQL | **no GitHub** (`JulioNCavalcanti`), commit `d4f52e9` |
| 4 | `fiap-15soat-oficina-lambda-auth` | Lambda de autenticação por CPF | **criado no GitHub** (`KauaAlmeidaSilveira`), repositório vazio — sem commits |

- [x] **Definir nomenclatura** — prefixo `fiap-15soat-oficina-` preservado; o repo atual não é renomeado
- [x] **`infra-k8s` montado e validado** — `vpc.tf`, `eks.tf`, `ecr.tf` copiados; `versions.tf` sem o provider New Relic; 3 outputs de rede novos (`vpc_id`, `private_subnets`, `private_subnet_cidrs`). `terraform validate` e `fmt` OK, zero variável obrigatória
- [x] **`infra-db` montado e validado** — `rds.tf` desacoplado do EKS e `data.tf` novo. `terraform validate` e `fmt` OK, só `db_password` obrigatória
- [x] **Acoplamento RDS → EKS quebrado** — `rds.tf` referenciava `module.eks.node_security_group_id`, a única aresta que impedia separar os dois repos de infraestrutura. Trocado por liberação via CIDR das subnets privadas, descobertas por data source
- [x] **Repo da aplicação reorganizado** — `infra/` removido, `observability/` criado com o Terraform do New Relic, `.gitignore` generalizado, referências órfãs corrigidas em `README.md`, `k8s/README.md`, `CLAUDE.md` e nos comentários dos manifestos
- [x] **`infra-k8s` e `infra-db` criados no GitHub e pushados** — `git init` em `main`, commit inicial, remote ligado. Local e remoto conferidos no mesmo hash, sem divergência
- [x] **README de `infra-k8s` e `infra-db`** — propósito, tecnologias, passos, diagrama da arquitetura e tabela de outputs. Documentam também as restrições do AWS Academy e a ordem de destruição (banco antes da VPC)
- [x] **Criar o repositório `lambda-auth`** — último dos quatro, criado em `KauaAlmeidaSilveira` (mesma conta da aplicação), mas ainda **vazio** (sem commits, sem README, sem esqueleto)
- [ ] **Criar o bucket S3 `fiap-15soat-oficina-tfstate` e a tabela DynamoDB `fiap-15soat-oficina-tflock`** — os três `versions.tf` já apontam para eles; precisam existir antes do primeiro `terraform init` com backend
- [x] **Pipeline de CI/CD nos dois repos de infra** — `.github/workflows/terraform.yml` em cada um (`8992338` e `76767b3`): `fmt`/`validate`/`plan` no PR com o plano comentado no próprio PR, `apply` no push para `main`. Validação roda sem backend, então sobrevive a credencial expirada; `apply` atrelado ao Environment `producao`; `concurrency group` evita disputa pelo lock do DynamoDB; no `infra-db` a senha entra como `TF_VAR_db_password`, sem virar arquivo no runner
- [ ] Cadastrar os secrets nos dois repos: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` e, no `infra-db`, `DB_PASSWORD`
- [ ] Configurar revisor obrigatório no Environment `producao` de cada repo de infra
- [ ] Pipeline de CI/CD do `lambda-auth` — repositório já existe (vazio); falta o esqueleto de código antes de fazer sentido montar o workflow
- [x] `.terraform.lock.hcl` do `observability/` gerado e commitado em `fbb1649` — trava só o provider `newrelic`
- [ ] Esqueleto do `lambda-auth` — campo `status` em `Cliente` já existe (PR #2); não bloqueia mais
- [x] `api` — `soat-architecture` confirmado como colaborador (evidência: `settings/access`, 15/09/2026)
- [x] `lambda-auth` — convite enviado a `soat-architecture` (evidência: `settings/access`, 15/09/2026). Aceite depende do outro lado; nossa parte está feita
- [x] `infra-k8s` — convite enviado a `soat-architecture` (evidência: `settings/access`, 15/09/2026). Aceite depende do outro lado; nossa parte está feita
- [x] `infra-db` — convite enviado a `soat-architecture` (evidência: `settings/access`, 15/09/2026). Aceite depende do outro lado; nossa parte está feita
- [ ] ⚠️ **Os repositórios estão em duas contas**: `api` e `lambda-auth` sob `KauaAlmeidaSilveira`, `infra-k8s` e `infra-db` sob `JulioNCavalcanti`. Secrets, proteção de branch e o convite ao `soat-architecture` precisam ser feitos em cada conta separadamente — combinar quem cuida de quais

### Regras de proteção

- [?] **Branch `main`/`master` protegida (sem commits diretos)** — não verificável via CLI (`gh` sem credenciais válidas). ⚠️ O histórico mostra commits direto no `master` (`98c5386`, `8441082`, `ea95f99`…) e **apenas 1 PR** em todo o projeto (`#1`), o que sugere que a proteção não está ativa
- [?] **Uso obrigatório de Pull Requests para merge** — mesma verificação pendente
- [ ] 🔸 **DECISÃO EM ABERTO — onde fica o ambiente de homologação.** O enunciado exige "deploy automático das branches de homologação e produção". Os pipelines de infraestrutura implementam só produção. Ninguém no grupo tem opinião formada ainda; decidir antes de gravar o vídeo.

  | Opção | Custo | Observação |
  |---|---|---|
  | Homologação nos repos de **infraestrutura** | alto — duplica EKS e RDS | Fiel ao enunciado, mas provavelmente estoura os créditos do Academy |
  | Homologação só no repo da **aplicação** | baixo — mesmo cluster, namespaces diferentes | Atende o requisito onde ele é barato; a infraestrutura fica com um ambiente só |
  | Ambientes por workspace do Terraform | médio | Um state por workspace, mas ainda cria recursos duplicados na AWS |

  Recomendação de quem implementou: a segunda. Exige criar a branch de homologação e um segundo bloco de deploy no workflow da aplicação.

### Infraestrutura obrigatória

- [ ] API Gateway para controle e roteamento
- [ ] Function Serverless para autenticação
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

### Pendente para o teste

| # | O quê | Quem | Depende de |
|---|---|---|---|
| 1 | Definir `alertas_email` em `infra/terraform.tfvars` | vocês | — |
| 2 | `terraform apply` dos dashboards e alertas | — | item 1 |
| 3 | Cadastrar `NEW_RELIC_LICENSE_KEY` nos GitHub Secrets | vocês | — |
| 4 | Confirmar que os nomes das métricas batem com as queries | — | app rodando |
| 5 | `helm upgrade --install` do `nri-bundle` | vocês | EKS no ar |
| 6 | Confirmar `trace.id` do agente chegando ao MDC | — | app com license key |
| 7 | Fechar a exposição pública de `/actuator/prometheus` | decisão | — |

**Só o item 5 precisa de AWS.** O caminho mais curto para destravar os itens 4 e 6:

```bash
docker-compose up --build           # com a license key no .env
curl -s localhost:8080/actuator/prometheus | grep oficina
```

Se os nomes saírem como `oficina_os_criadas_total`, `oficina_os_tempo_status_seconds_sum` e
`oficina_integracao_falhas_total`, as queries estão corretas. Se não, ajustar os `.tf`.

### Risco conhecido

As queries NRQL foram validadas quanto à **sintaxe**, não quanto aos **nomes das métricas** — estes
assumem a convenção que o Micrometer aplica ao exportar para Prometheus, e só se confirmam com dado
chegando. É a primeira coisa a checar quando a aplicação subir.

## 4. Documentação da Arquitetura

- [x] **Diagrama de Componentes com visão de nuvem, APIs, banco e monitoramento** — `docs/diagrama-infraestrutura.png` redesenhado com uma 4ª coluna de Observabilidade (New Relic: nri-bundle, agente APM, logs, dashboards, alertas), mantendo as 3 colunas da Fase 2 (Pipeline CI/CD, Infraestrutura AWS, Runtime Kubernetes). API Gateway e Lambda não entram — não existe código para eles ainda. `docs/diagrama-componentes.png` (Clean Architecture, camadas de código) não muda, não é o que este item do desafio pede
- [ ] **Diagrama de Sequência** para o fluxo de **autenticação** (CPF → API Gateway → Lambda → JWT → API) — bloqueado até fechar o RFC da estratégia de autenticação
- [x] **Diagrama de Sequência** para o fluxo de **abertura de ordem de serviço** — `docs/sequencia-abertura-os.md`, Mermaid, cobre a criação da OS (`POST /api/ordens-servico`) conforme a redação literal do PDF do desafio
- [x] **RFCs** para decisões técnicas relevantes — `docs/rfc/`: [0001 escolha da nuvem (AWS)](docs/rfc/0001-escolha-da-nuvem-aws.md), [0002 escolha do banco (RDS PostgreSQL)](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md). Falta só o RFC da estratégia de autenticação, que fica bloqueado (ver Seção 1)
- [x] **ADRs** para decisões arquiteturais permanentes — `docs/adr/`: [0001 uso de HPA](docs/adr/0001-uso-de-hpa-para-escalabilidade.md), [0002 comunicação síncrona via REST](docs/adr/0002-comunicacao-sincrona-via-rest.md)
- [x] **Justificativa formal da escolha do banco de dados** — absorvida no [RFC-0002](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md), evita duplicar o mesmo conteúdo em dois documentos
- [x] **Ajustes no modelo relacional + diagrama ER + explicação dos relacionamentos** — campo `status` do cliente já existe (PR #2); `docs/diagrama-er.png` gerado via Mermaid a partir de `docs/diagrama-er.mmd` (texto, versionável), conferido campo a campo contra as 10 entidades reais; explicação escrita em [`docs/modelo-relacional.md`](docs/modelo-relacional.md)
- [x] Endereçar "consistência e performance" do banco — documentado no [RFC-0002](docs/rfc/0002-escolha-do-banco-de-dados-rds-postgresql.md): FKs obrigatórias, restrições de unicidade, fronteira transacional por gateway, `FetchType.LAZY`

## 5. Entregáveis

### Repositórios Git

- [ ] 4 repositórios separados com código, CI/CD e instruções claras no `README.md`
- [ ] Dockerfiles em cada repositório (quando aplicável)
- [ ] Pipelines de CI/CD funcionais em cada repositório
- [ ] Links para os deploys ativos

### README.md de cada repositório

Cada critério (propósito, tecnologias, passos, diagrama) é checado por repositório — um agregado tipo "2 de 4" escondia qual repo falta o quê.

- [x] `infra-k8s` — propósito, tecnologias, passos de execução e diagrama da arquitetura presentes no README (confirmado na linha de "README de infra-k8s e infra-db" acima)
- [x] `infra-db` — propósito, tecnologias, passos de execução e diagrama da arquitetura presentes no README (idem)
- [x] `api` — README tem "Sobre o Projeto" (propósito), "Stack tecnológica", "Como executar" e os dois diagramas embutidos (`diagrama-componentes.png`, `diagrama-infraestrutura.png`). Ressalva: os diagramas são os mesmos da Fase 2 e ainda não mostram API Gateway/Lambda/observabilidade — acompanha o item da Seção 4 acima, não bloqueia este checkbox
- [ ] `lambda-auth` — repositório vazio; nenhum dos quatro critérios existe ainda
- [x] Link para o Swagger/Postman das APIs — `postman/oficina-api.collection.json` + `.environment.json` criados, cruzados contra os controllers reais (2 bugs corrigidos: corpo faltando em "Aprovar OS", variável errada em "Remove Item à OS") e completados com o endpoint novo do PR #2 (`PATCH /clientes/{id}/status`) e o de aprovação pública que o README já prometia

### Vídeo de demonstração (YouTube/Vimeo, até 15 minutos)

- [ ] Autenticação com CPF
- [ ] Execução da pipeline CI/CD
- [ ] Deploy automatizado
- [ ] Consumo das APIs protegidas
- [ ] Dashboard de monitoramento com análise ao vivo
- [ ] Logs e traces em execução

### Entrega no Portal do Aluno

- [ ] PDF único com links dos 4 repositórios
- [ ] Link do vídeo (até 15 minutos)
- [ ] Links das documentações
- [x] Confirmação do usuário `soat-architecture` adicionado a **todos** os repositórios — feito nos 4 (`api` confirmado; `lambda-auth`, `infra-k8s`, `infra-db` com convite enviado, aceite depende do outro lado) — ver Seção 2

---

## Pontos de atenção que atravessam as frentes

1. **Modelagem do cliente** — o `status` exigido pela Lambda não existe hoje; a mudança atinge domínio, entidade JPA, mappers MapStruct, DTOs e o diagrama ER de uma vez.
2. **Autenticação muda de eixo** — sair de `username` + senha (`AuthUseCase`) para CPF via Lambda afeta `SecurityConfig`, os `@PreAuthorize` dos controllers, o `DataLoader` (usuários semeados) e todos os `*ControllerIT` que usam `@WithMockUser`.
3. **Separar `infra/` em dois repositórios** exige decidir como o `terraform output` do repo de banco chega ao repo de K8s e ao pipeline da aplicação (hoje tudo vem de secrets do GitHub definidos à mão).
4. **Gate de cobertura JaCoCo (80%)** continua valendo em `core.usecase`, `core.domain.entity` e `entrypoint.controller` — código novo nesses pacotes sem teste quebra o `mvn verify` da CI.
5. **State do Terraform é local e não existe nesta máquina** — impede tanto retomar a infra atual com segurança quanto atender ao requisito de deploy automático em 4 repositórios. Ver Seção 2.
6. **Credenciais AWS são temporárias (Academy)** — o workflow usa `AWS_SESSION_TOKEN`. Toda retomada começa por renovar as credenciais locais **e** os três GitHub Secrets, senão o pipeline falha no deploy.
7. **`/actuator/prometheus` está público** — liberado no `SecurityConfig` para o coletor alcançá-lo, mas o `Service` é `LoadBalancer`, então fica exposto na internet. Não vaza segredo; vaza nomes de endpoints e detalhes de JVM. Fechar movendo o actuator para uma porta de management separada, o que obriga a ajustar as probes do `deployment.yaml`.

---

## Higiene do repositório

- [x] Chaves JWT ignoradas em qualquer diretório — a regra antiga só cobria `src/main/resources/`, e as chaves geradas na raiz escapavam. Confirmado que nenhuma chave chegou a ser rastreada em commit algum.
- [ ] **Documentação não commitada** — `CLAUDE.md`, `CHECKLIST.md` e `docs/superpowers/` seguem não rastreados. Merecem um commit `docs:` próprio.
- [x] Corrigir a referência à pasta `postman/` no `README.md` — pasta criada com collection + environment reais; a referência já estava correta, só faltavam os arquivos.
- [x] `.gitignore` do Terraform generalizado — os padrões eram `infra/terraform.tfvars`, `infra/.terraform/`; agora casam em qualquer diretório, mesma correção aplicada às chaves JWT.
- [x] O `CHECKLIST.md` da Fase 2 (que referenciava `infra/eks.tf`, `infra/rds.tf` etc., já removidos deste repo) foi substituído por este arquivo na renomeação — não existe mais como arquivo separado. Conteúdo antigo continua recuperável via git (`git show 98c5386:CHECKLIST.md`), nada commitado foi perdido.
