# Observabilidade — dashboards e alertas do New Relic

Terraform que cria, na conta New Relic, os dashboards e as condições de alerta que monitoram
esta aplicação.

Vive **neste repositório**, e não no de infraestrutura, porque as queries NRQL citam por string
os nomes das métricas emitidas por
`src/main/java/br/com/fiap/oficina/dataprovider/observabilidade/MicrometerMetricasGateway.java`.
Mantendo os dois juntos, renomear uma métrica e ajustar o dashboard cabe no mesmo pull request;
separados, um rename no Java quebraria o outro repositório sem nenhum sinal em tempo de compilação.

## O que é criado

| Arquivo | Recursos |
|---|---|
| `newrelic.tf` | Um dashboard com duas páginas: negócio (volume diário de OS, tempo médio por status, transições) e saúde (falhas de integração, latência, taxa de erro, endpoints mais lentos) |
| `newrelic_alertas.tf` | Policy, 4 condições NRQL (falhas no processamento de OS, taxa de erro, latência, aplicação sem reportar) e notificação por e-mail |

Não há nenhum recurso AWS aqui — o provider é outro e o state é separado.

## Como aplicar

```bash
cp terraform.tfvars.example terraform.tfvars   # preencher a NRAK- e o e-mail
terraform init
terraform plan
terraform apply
```

## Contratos com o resto do projeto

Nenhum destes é verificável por compilador:

1. **`newrelic_app_name`** precisa ser igual a `NEW_RELIC_APP_NAME` em `k8s/configmap.yaml`.
2. **Os nomes das métricas** nas queries NRQL seguem a convenção que o Micrometer aplica ao
   exportar para Prometheus: `oficina.os.criadas` vira `oficina_os_criadas_total`, e um `Timer`
   vira `_sum` e `_count`. Confirmar contra dados reais na primeira ingestão.
3. A coleta depende das anotações `prometheus.io/scrape` em `k8s/deployment.yaml` e do
   `nri-bundle` instalado no cluster (`k8s/newrelic-values.yaml`).
