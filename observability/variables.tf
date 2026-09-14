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

# ATENCAO: precisa ser identico ao NEW_RELIC_APP_NAME de k8s/configmap.yaml.
# Se divergir, todo dashboard e toda condicao de alerta passam a casar com
# nada — sem erro, so vazio.
variable "newrelic_app_name" {
  description = "Nome da aplicacao no APM. Contrato compartilhado com k8s/configmap.yaml."
  type        = string
  default     = "oficina-api"
}

variable "alertas_email" {
  description = "E-mail que recebe as notificacoes de alerta"
  type        = string
}
