# Politica de alertas e notificacao por e-mail.
#
# O requisito da Fase 3 cita explicitamente "alertas para falhas no processamento
# de ordens de servico" — a condicao falhas_integracao cobre esse caso, alimentada
# pelo contador que MetricasGateway.falhaDeIntegracao incrementa.

resource "newrelic_alert_policy" "oficina" {
  name                = "Oficina API - Operacao"
  incident_preference = "PER_CONDITION_AND_TARGET"
}

resource "newrelic_nrql_alert_condition" "falhas_integracao" {
  account_id  = var.newrelic_account_id
  policy_id   = newrelic_alert_policy.oficina.id
  name        = "Falha no processamento de ordens de servico"
  description = "Qualquer falha registrada em oficina_integracao_falhas_total (SMTP, notificacao de aprovacao)."
  type        = "static"
  enabled     = true

  nrql {
    query = "SELECT sum(oficina_integracao_falhas_total) FROM Metric"
  }

  critical {
    operator              = "above"
    threshold             = 0
    threshold_duration    = 300
    threshold_occurrences = "at_least_once"
  }

  aggregation_window           = 60
  aggregation_method           = "event_flow"
  aggregation_delay            = 120
  violation_time_limit_seconds = 86400
}

resource "newrelic_nrql_alert_condition" "taxa_de_erro" {
  account_id  = var.newrelic_account_id
  policy_id   = newrelic_alert_policy.oficina.id
  name        = "Taxa de erro da API acima de 5%"
  description = "Percentual de transacoes com erro na aplicacao."
  type        = "static"
  enabled     = true

  nrql {
    query = "SELECT percentage(count(*), WHERE error IS true) FROM Transaction WHERE appName = '${var.newrelic_app_name}'"
  }

  critical {
    operator              = "above"
    threshold             = 5
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  aggregation_window           = 60
  aggregation_method           = "event_flow"
  aggregation_delay            = 120
  violation_time_limit_seconds = 86400
}

resource "newrelic_nrql_alert_condition" "latencia" {
  account_id  = var.newrelic_account_id
  policy_id   = newrelic_alert_policy.oficina.id
  name        = "Latencia media acima de 2 segundos"
  description = "Tempo medio de resposta das transacoes web."
  type        = "static"
  enabled     = true

  nrql {
    query = "SELECT average(duration) FROM Transaction WHERE appName = '${var.newrelic_app_name}'"
  }

  critical {
    operator              = "above"
    threshold             = 2
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  aggregation_window           = 60
  aggregation_method           = "event_flow"
  aggregation_delay            = 120
  violation_time_limit_seconds = 86400
}

# A aplicacao parou de reportar: cobre o requisito de uptime sem depender de Synthetics.
resource "newrelic_nrql_alert_condition" "sem_dados" {
  account_id  = var.newrelic_account_id
  policy_id   = newrelic_alert_policy.oficina.id
  name        = "Aplicacao sem reportar dados"
  description = "Nenhuma transacao recebida — indica pods fora do ar ou agente desconectado."
  type        = "static"
  enabled     = true

  nrql {
    query = "SELECT count(*) FROM Transaction WHERE appName = '${var.newrelic_app_name}'"
  }

  critical {
    operator              = "below"
    threshold             = 1
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  aggregation_window           = 60
  aggregation_method           = "event_flow"
  aggregation_delay            = 120
  violation_time_limit_seconds = 86400
}

# ---------------------------------------------------------------------------
# Notificacao por e-mail
# ---------------------------------------------------------------------------

resource "newrelic_notification_destination" "email" {
  account_id = var.newrelic_account_id
  name       = "Oficina - e-mail do time"
  type       = "EMAIL"

  property {
    key   = "email"
    value = var.alertas_email
  }
}

resource "newrelic_notification_channel" "email" {
  account_id     = var.newrelic_account_id
  name           = "Oficina - canal de e-mail"
  type           = "EMAIL"
  destination_id = newrelic_notification_destination.email.id
  product        = "IINT"

  property {
    key   = "subject"
    value = "{{ issueTitle }}"
  }
}

resource "newrelic_workflow" "oficina" {
  account_id            = var.newrelic_account_id
  name                  = "Oficina - notificar falhas"
  muting_rules_handling = "NOTIFY_ALL_ISSUES"

  issues_filter {
    name = "politica-oficina"
    type = "FILTER"

    predicate {
      attribute = "labels.policyIds"
      operator  = "EXACTLY_MATCHES"
      values    = [newrelic_alert_policy.oficina.id]
    }
  }

  destination {
    channel_id = newrelic_notification_channel.email.id
  }
}
