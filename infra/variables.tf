variable "aws_region" {
  description = "Região AWS onde os recursos serão criados"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefixo usado no nome de todos os recursos"
  type        = string
  default     = "oficina"
}

variable "vpc_cidr" {
  description = "CIDR block da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "AZs usadas para as subnets públicas/privadas (mínimo 2, exigido pelo EKS/RDS)"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "eks_cluster_version" {
  description = "Versão do Kubernetes no EKS"
  type        = string
  default     = "1.30"
}

variable "node_instance_type" {
  description = "Tipo de instância EC2 dos nodes do EKS"
  type        = string
  default     = "t3.medium"
}

variable "node_desired_size" {
  description = "Quantidade desejada de nodes no node group (a aplicação escala pods via HPA nesses nodes, não os nodes em si)"
  type        = number
  default     = 2
}

variable "node_min_size" {
  type    = number
  default = 1
}

variable "node_max_size" {
  type    = number
  default = 3
}

variable "db_instance_class" {
  description = "Classe de instância do RDS"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  type    = string
  default = "oficina"
}

variable "db_username" {
  type    = string
  default = "oficina"
}

variable "db_password" {
  description = "Senha do banco RDS. Não definir default aqui — passe via terraform.tfvars (gitignorado) ou variável de ambiente TF_VAR_db_password."
  type        = string
  sensitive   = true
}

variable "db_allocated_storage" {
  description = "Armazenamento em GB do RDS"
  type        = number
  default     = 20
}

variable "db_engine_version" {
  description = "Versão do PostgreSQL no RDS (mesma major version usada em dev/prod: 16)"
  type        = string
  default     = "16.14"
}

# ---------------------------------------------------------------------------
# New Relic
# ---------------------------------------------------------------------------

variable "newrelic_account_id" {
  description = "Account ID do New Relic (identificador, nao e segredo)"
  type        = number
}

variable "newrelic_api_key" {
  description = "User API key do New Relic (comeca com NRAK-). Nunca commitar."
  type        = string
  sensitive   = true
}

variable "newrelic_region" {
  description = "Regiao da conta New Relic: US ou EU. Definida na criacao da conta e irreversivel."
  type        = string
  default     = "US"

  validation {
    condition     = contains(["US", "EU"], var.newrelic_region)
    error_message = "newrelic_region precisa ser US ou EU."
  }
}

variable "newrelic_app_name" {
  description = "Nome da aplicacao no APM, igual ao NEW_RELIC_APP_NAME do ConfigMap"
  type        = string
  default     = "oficina-api"
}

variable "alertas_email" {
  description = "E-mail que recebe as notificacoes de alerta"
  type        = string
}
