# Infraestrutura como Código (Terraform) — AWS

Provisiona a infraestrutura cloud usada pela aplicação: cluster Kubernetes (EKS), banco de dados
gerenciado (RDS PostgreSQL) e o registry de imagens (ECR). Os manifestos que rodam *dentro* desse
cluster estão em `/k8s` — esta pasta só cuida do que existe *fora* do Kubernetes.

## Recursos criados

| Recurso | Arquivo | Descrição |
|---|---|---|
| VPC + subnets públicas/privadas + NAT Gateway | `vpc.tf` | Rede isolada para o cluster e o banco (módulo oficial `terraform-aws-modules/vpc/aws`) |
| Cluster EKS + node group gerenciado | `eks.tf` | Kubernetes gerenciado (módulo oficial `terraform-aws-modules/eks/aws`); nodes em subnet privada; role dos nodes já com permissão de leitura no ECR |
| Repositório ECR | `ecr.tf` | Registry de imagens Docker da aplicação, com scan de vulnerabilidade automático e política de retenção (10 imagens mais recentes) |
| RDS PostgreSQL | `rds.tf` | Banco gerenciado, single-AZ, não publicamente acessível — só aceita conexão dos nodes do EKS (security group dedicado) |

**Não criado por Terraform:** o `metrics-server` (exigido pelo HPA em `k8s/hpa.yaml`) não é um addon
nativo do EKS — instale depois do cluster estar no ar:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

## ⚠️ Isto cria recursos cobrados pela AWS

Cluster EKS (control plane ~US$0,10/hora), instâncias EC2 dos nodes, NAT Gateway, RDS e armazenamento
geram custo enquanto estiverem no ar. Depois de gravar o vídeo/testar, rode `terraform destroy`
(seção abaixo) para não deixar nada cobrando indevidamente.

## Pré-requisitos

- Terraform >= 1.5 (testado com 1.15)
- Conta AWS com credenciais configuradas localmente (`aws configure` ou variáveis
  `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_SESSION_TOKEN`)
- AWS CLI instalado (usado depois do apply, para configurar o `kubectl`)
- Permissões IAM suficientes para criar VPC, EKS, RDS, ECR e roles/policies associadas

## Como aplicar

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# edite terraform.tfvars com a senha real do banco (nunca commitar esse arquivo)

terraform init
terraform plan   # revisa o que será criado
terraform apply  # confirma e cria de fato (~15-20 min, EKS demora para ficar pronto)
```

## Depois do apply

```bash
terraform output
```

Isso mostra:
- `configure_kubectl_command` — rode o comando exibido pra apontar seu `kubectl` local pro cluster criado
- `rds_endpoint` — coloque em `k8s/configmap.yaml` no lugar de `<RDS_ENDPOINT>`
- `ecr_repository_url` — use no lugar de `<ECR_URI>` em `k8s/deployment.yaml` (e no pipeline de CI/CD)

Depois de preencher esses dois placeholders, siga as instruções de `k8s/README.md` para gerar o
Secret e aplicar os manifestos no cluster recém-criado.

## Destruindo tudo

```bash
terraform destroy
```

Remove todos os recursos criados por este diretório (EKS, RDS, VPC, ECR — a imagem dentro do ECR
também é apagada). Rode isso assim que não precisar mais do ambiente no ar.
