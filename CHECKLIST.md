# Checklist de Entrega — Tech Challenge Fase 2

> Decisões técnicas tomadas: **Clean Architecture** (não hexagonal); notificação de status por e-mail via **SMTP do Gmail**; infraestrutura cloud em **AWS (EKS + RDS)**.

## Evolução da aplicação

### Refatoração
- [ ] Clean Code (nomes claros, simplicidade, coesão) — revisar durante a refatoração de arquitetura
- [ ] Clean Architecture (separar em `domain` / `application` (casos de uso) / `infrastructure` (JPA, web, security, email) — hoje o projeto é uma arquitetura em camadas simples (`controller/service/domain/dto`), sem essa separação
- [x] Testes automatizados (unitários e/ou integração) cobrindo os fluxos críticos — já existem 124 testes (unit + IT), cobertura JaCoCo ≥ 80% (reavaliar após mover pacotes na refatoração)

### APIs — alterar/criar
- [x] Abertura de Ordem de Serviço (OS): `POST /api/ordens-servico` recebe cliente/veículo/itens e retorna `numero` único
- [x] Consulta de status da OS: `GET /{id}` e `GET /numero/{numero}` retornam a situação atual (inclui todos os status do enum `StatusOS`, incluindo `REPROVADA`)
- [x] Aprovação de orçamento: `PATCH /{id}/aprovar` com `{ "aprovado": true|false }` — já trata aprovação e recusa (recusa move para `REPROVADA`)
- [x] Listagem de ordens de serviço — `listarTodas()` usa `findByStatusNotIn` + ordenação em memória; `?status=` e `?clienteId=` mantêm o comportamento anterior (histórico completo/qualquer status)
  - [x] Ordenação por status: Em Execução > Aguardando Aprovação > Diagnóstico > Recebida
  - [x] Dentro do mesmo status, mais antigas primeiro (`criadoEm` ascendente)
  - [x] Exclusão lógica (não física) das OS finalizadas, entregues e reprovadas da listagem padrão (filtro de query, sem coluna de soft-delete)
- [ ] Atualização de status da OS via e-mail (notificação de saída ao avançar status, via SMTP do Gmail) — ponto pendente confirmado, nada implementado ainda

## Infraestrutura

### Conteinerização
- [x] Dockerfile (existente da Fase 1) — revisar/atualizar se necessário após a refatoração
- [x] docker-compose para desenvolvimento local (existente da Fase 1) — revisar/atualizar se necessário

### Orquestração com Kubernetes (K8s)
- [x] Deployments — `k8s/deployment.yaml` (probes em `/actuator/health`, resources definidos, chaves JWT montadas via Secret para funcionar com múltiplas réplicas)
- [x] Services — `k8s/service.yaml` (`LoadBalancer`, porta 80 → 8080)
- [x] ConfigMaps (variáveis não sensíveis) — `k8s/configmap.yaml` (perfil ativo, host do DB, paths das chaves JWT)
- [x] Secrets (variáveis sensíveis: credenciais DB, tokens JWT) — `k8s/secret.yaml.example` (template; valor real gerado via `kubectl create secret`, nunca commitado)
- [x] Horizontal Pod Autoscaler (HPA) escalando conforme consumo de CPU/memória — `k8s/hpa.yaml` (min 1, max 2 réplicas, alvo 70% CPU)
- [ ] Placeholders `<RDS_ENDPOINT>` (configmap) e `<ECR_URI>` (deployment) substituídos pelos valores reais após a fase de Terraform/CI-CD
- [x] Testado fim-a-fim em cluster real (Docker Desktop Kubernetes, kubeadm, nó único): imagem buildada localmente + Postgres local temporário substituindo os placeholders de RDS/ECR só para o teste. Login, listagem de OS e HPA validados; HPA escalou de 1→2 réplicas sob carga real (`SuccessfulRescale`) e o mesmo token JWT foi aceito nas duas réplicas (20/20 requisições 200, sem 401), confirmando que o Secret compartilhado resolve o problema de chaves por-pod. Recursos de teste removidos do cluster ao final.

### Infraestrutura como Código (IaC) — AWS
- [x] Terraform: cluster EKS — `infra/eks.tf` (módulo oficial, node group gerenciado, role dos nodes já com policy de leitura no ECR)
- [x] Terraform: banco de dados RDS (PostgreSQL) — `infra/rds.tf` (single-AZ, não publicamente acessível, security group restrito aos nodes do EKS)
- [x] Terraform: recursos de rede/suporte (VPC, subnets, security groups) — `infra/vpc.tf` (módulo oficial, subnets públicas/privadas, NAT gateway único para reduzir custo)
- [x] Terraform: repositório ECR — `infra/ecr.tf` (scan de vulnerabilidade on push, lifecycle policy)
- [x] Documentação dos recursos criados e de como aplicar — `infra/README.md` (inclui aviso de custo e passo a passo de `destroy`)
- [x] `terraform init` + `terraform validate` rodados localmente com sucesso (sem aplicar — exigiria credenciais AWS reais e geraria custo; apply/destroy ficam a cargo do usuário)
- [ ] Rodar `terraform apply` de verdade contra uma conta AWS (fora do escopo deste ambiente — precisa das credenciais reais do aluno)

### CI/CD
- [ ] Pipeline configurada (GitHub Actions)
- [ ] Etapa de build da aplicação
- [ ] Etapa de execução dos testes automatizados
- [ ] Etapa de build da imagem Docker
- [ ] Etapa de deploy no cluster Kubernetes (EKS)
- [ ] Etapa de deploy/migração do banco de dados (RDS)
- [ ] Etapa de aplicação dos manifestos YAML no cluster

## Entregáveis da Fase 2

### Repositório git (mesmo da Fase 1)
- [ ] Código-fonte atualizado e refatorado em Clean Architecture
- [ ] Dockerfile e docker-compose revisados
- [x] Manifestos Kubernetes em `/k8s`
- [x] Scripts Terraform em `/infra`
- [ ] Arquivos de configuração da pipeline CI/CD (`.github/workflows`)

### README.md atualizado
- [ ] Descrição da solução e dos objetivos desta fase
- [ ] Desenho da arquitetura proposta
  - [ ] Componentes da aplicação (Clean Architecture)
  - [ ] Infraestrutura provisionada (AWS: EKS, RDS, etc.)
  - [ ] Fluxo de deploy (CI/CD → Docker → Terraform → K8s)
- [x] Instruções de execução local (já existiam da Fase 1)
- [x] Instruções de deploy em Kubernetes (seção adicionada, linka `k8s/README.md`)
- [x] Instruções de provisionamento da infraestrutura com Terraform (seção adicionada, linka `infra/README.md`)
- [ ] Link para a collection completa das APIs (Postman já existe em `Challenge - Fase 1/postman/` — atualizar e linkar)
- [ ] Link para vídeo demonstrativo (YouTube/Vimeo, público ou não listado, até 15 min) demonstrando:
  - [ ] Deploy da aplicação
  - [ ] Execução do CI/CD
  - [ ] Consumo das APIs
  - [ ] Escalabilidade automática (pode simular aumento de carga ou múltiplas OS)

### Entrega no portal do aluno
- [ ] PDF contendo o link do repositório GitHub compartilhado com o usuário `soat-architecture`
- [ ] PDF contendo o desenho da arquitetura com os recursos escolhidos
- [ ] PDF contendo o link do vídeo (até 15 minutos) apresentando a solução desenvolvida
