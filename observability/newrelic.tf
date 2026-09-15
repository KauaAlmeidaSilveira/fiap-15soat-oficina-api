# Dashboard exigido pela Fase 3: volume diario de OS, tempo medio por status e
# erros nas integracoes, mais latencia e saude da aplicacao.
#
# As metricas oficina_* chegam via /actuator/prometheus, coletado pelo nri-bundle.
# O sufixo _total e a expansao _sum/_count sao convencao do formato Prometheus,
# aplicada pelo Micrometer sobre os nomes declarados em MicrometerMetricasGateway.

locals {
  nr_app = var.newrelic_app_name
}

resource "newrelic_one_dashboard" "oficina_operacao" {
  name        = "Oficina API - Operacao"
  description = "Visao operacional da API da oficina: negocio, integracoes e saude."

  page {
    name        = "Ordens de servico"
    description = "Metricas de negocio derivadas das transicoes de status da OS."

    widget_line {
      title  = "Volume diario de ordens de servico"
      row    = 1
      column = 1
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT rate(sum(oficina_os_criadas_total), 1 day) AS 'OS criadas' FROM Metric TIMESERIES AUTO"
      }
    }

    widget_line {
      title  = "Tempo medio no status, em minutos"
      row    = 1
      column = 7
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        # O filtro por metricName e obrigatorio: sem ele, FACET status tambem agrupa o label
        # status (codigo HTTP) das metricas http_server_requests.
        query = "SELECT sum(oficina_os_tempo_status_seconds_sum) / sum(oficina_os_tempo_status_seconds_count) / 60 AS 'minutos' FROM Metric WHERE metricName IN ('oficina_os_tempo_status_seconds_sum', 'oficina_os_tempo_status_seconds_count') FACET status TIMESERIES AUTO"
      }
    }

    widget_bar {
      title  = "Transicoes de status no periodo"
      row    = 4
      column = 1
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT sum(oficina_os_transicoes_total) FROM Metric FACET para SINCE 1 day ago"
      }
    }

    widget_billboard {
      title  = "OS criadas nas ultimas 24h"
      row    = 4
      column = 7
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT rate(sum(oficina_os_criadas_total), 1 day) AS 'OS' FROM Metric SINCE 1 day ago"
      }
    }
  }

  page {
    name        = "Integracoes e saude"
    description = "Falhas nas integracoes externas, latencia e erros da API."

    widget_line {
      title  = "Falhas nas integracoes"
      row    = 1
      column = 1
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT sum(oficina_integracao_falhas_total) FROM Metric FACET integracao, motivo TIMESERIES AUTO"
      }
    }

    widget_line {
      title  = "Latencia das APIs"
      row    = 1
      column = 7
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT average(duration) * 1000 AS 'ms' FROM Transaction WHERE appName = '${local.nr_app}' TIMESERIES AUTO"
      }
    }

    widget_line {
      title  = "Taxa de erro da aplicacao"
      row    = 4
      column = 1
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT percentage(count(*), WHERE error IS true) AS '% com erro' FROM Transaction WHERE appName = '${local.nr_app}' TIMESERIES AUTO"
      }
    }

    widget_table {
      title  = "Endpoints mais lentos"
      row    = 4
      column = 7
      width  = 6
      height = 3

      nrql_query {
        account_id = var.newrelic_account_id
        query      = "SELECT average(duration) * 1000 AS 'ms', count(*) AS 'chamadas' FROM Transaction WHERE appName = '${local.nr_app}' FACET name SINCE 1 hour ago LIMIT 20"
      }
    }
  }
}
