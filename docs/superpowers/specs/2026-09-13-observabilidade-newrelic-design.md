# Observabilidade com New Relic — Design

> Tech Challenge Fase 3 · Seção "Monitoramento e Observabilidade"
> Data: 2026-09-13, revisado em 14/09/2026 após a separação dos repositórios
> Status: implementado e commitado (`14a1e8c`, `fbb1649`); falta aplicar contra infraestrutura no ar

## Contexto

A Fase 3 exige monitoramento de latência, recursos do Kubernetes, healthchecks e uptime;
alertas para falhas no processamento de ordens de serviço; logs estruturados em JSON com
correlação entre requisições; e dashboards com volume diário de OS, tempo médio de execução
por status e erros nas integrações.

Hoje o repositório não tem nada disso: nenhuma referência a ferramenta de APM, nenhuma
configuração de logging (a aplicação usa o padrão texto do Spring Boot), e as únicas fontes
de sinal são `/actuator/health` (usado pelas probes) e o `metrics-server` que alimenta o HPA.

## Decisões já tomadas

| Decisão | Escolha | Razão |
|---|---|---|
| Ferramenta | New Relic | O free tier cobre APM, logs, dashboards e alertas. O do Datadog não inclui APM nem logs. |
| Conta | Free, região US | Sem custo e sem cartão. Região US casa com a infra em `us-east-1` e é irreversível após a criação. |
| Instrumentação de logs | Agente nativo do New Relic, não OpenTelemetry | O requisito pede JSON com correlação, não OTel. Um agente só, sem collector. O "por que não OTel" vira ADR. |
| Métricas de negócio | Porta `MetricasGateway` no core | Preserva a regra de dependência da Clean Architecture. Espelha `NotificacaoAprovacaoGateway`. |
| Dashboards e alertas | Terraform, provider `newrelic` | Versionados e revisáveis em PR; casa com o tema de IaC da fase. |
| Onde mora o código | `observability/` neste repositório | A separação em 4 repos foi feita em `fbb1649`. O Terraform do New Relic ficou aqui porque as queries NRQL são acopladas por string aos nomes das métricas do código Java. |

## Restrição arquitetural que governa o desenho

`core/` é livre de framework por regra do projeto — sem Spring, sem JPA, sem slf4j.
O `OrdemServicoUseCase` chega a logar com `System.Logger` do JDK para não importar slf4j.

As duas métricas de negócio exigidas nascem exatamente onde o status muda
(`OrdemServicoUseCase.avancarStatus()` e `aprovar()`). Chamar `MeterRegistry` ou a API do
New Relic ali dentro quebraria a arquitetura que o projeto inteiro defende.

Por isso a instrumentação entra como **porta de saída**, igual ao e-mail de aprovação.

## Componentes

### 1. `MetricasGateway` — porta no core

`core/gateway/MetricasGateway.java`:

```java
public interface MetricasGateway {
    void ordemServicoCriada();
    void transicaoDeStatus(StatusOS de, StatusOS para, Duration tempoNoStatusAnterior);
    void falhaDeIntegracao(String integracao, String motivo);
}
```

Regras:

- **Nenhum identificador de OS nos parâmetros.** Id ou número em tag de métrica explode a
  cardinalidade e queima a cota de ingest. O que identifica as séries são os enums de status,
  que têm 7 valores.
- Os três métodos cobrem os três dashboards exigidos: contagem para volume diário, `Duration`
  para tempo por status, e falhas para erros de integração.
- O cálculo da duração é feito no core (o use case sabe quando o status mudou); o dataprovider
  só publica.

### 2. `statusAlteradoEm` — ajuste no modelo

O PDF pede tempo médio **por status**, citando Diagnóstico, Execução e Finalização. A entidade
hoje tem `criadoEm`, `dataInicio`, `dataFim`, `dataEntrega` e `dataAprovacao`, o que permite
calcular a duração de `EM_EXECUCAO` (`dataInicio`→`dataFim`) mas **não** a de `EM_DIAGNOSTICO`
nem a de `AGUARDANDO_APROVACAO` — nada registra quando a OS entrou nesses status.

Solução: campo `statusAlteradoEm` em `OrdemServico`, atualizado em `avancarStatus()` e
`aprovar()`. O use case calcula `agora - statusAlteradoEm` e passa a `Duration` pronta.

Arquivos atingidos:

- `core/domain/entity/OrdemServico.java` — campo + atribuição nas transições
- `dataprovider/persistence/entity/OrdemServico.java` — coluna
- `dataprovider/gateway/OrdemServicoGatewayImpl.salvar()` — copiar o campo (o método seta
  campo a campo, não é automático)
- O mapper MapStruct mapeia por nome, sem alteração

Efeito colateral desejável: a coluna responde "há quanto tempo esta OS está parada?", e o
ajuste é exatamente o tipo de mudança no modelo relacional que a Seção 4 do PDF exige
justificar — o mesmo trabalho paga dois requisitos.

### 3. `MicrometerMetricasGateway` — implementação no dataprovider

`dataprovider/observabilidade/MicrometerMetricasGateway.java`, `@Component`, recebendo
`MeterRegistry` por injeção.

| Método | Instrumento | Tags |
|---|---|---|
| `ordemServicoCriada` | `Counter` `oficina.os.criadas` | — |
| `transicaoDeStatus` | `Counter` `oficina.os.transicoes` + `Timer` `oficina.os.tempo.status` | `de`, `para` / `status` |
| `falhaDeIntegracao` | `Counter` `oficina.integracao.falhas` | `integracao`, `motivo` |

`motivo` precisa ser um conjunto fechado de valores (classe da exceção, não a mensagem), de
novo por cardinalidade.

### 4. Logs estruturados

- `logstash-logback-encoder` no `pom.xml`
- `logback-spring.xml` com `<springProfile>`: perfil `dev` mantém texto legível no console;
  perfil `default` emite JSON — mesmo mecanismo de perfil que o projeto já usa para trocar
  banco e implementação de e-mail
- Correlação: `trace.id` e `span.id` injetados pelo agente New Relic no MDC, incluídos no JSON
- **Desligar `spring.jpa.show-sql` e `hibernate.format_sql` no perfil `default`.** Hoje estão
  `true` em produção (`application.properties:12-13`); cada query vira várias linhas formatadas,
  poluindo a busca e consumindo a cota de 100 GB/mês à toa

### 5. Agente APM — implementado

- `newrelic.jar` 9.4.0 baixado num estágio dedicado do `Dockerfile` (URL e caminho
  `newrelic/newrelic.jar` verificados contra o servidor de download)
- `docker-entrypoint.sh` só acrescenta `-javaagent` quando `NEW_RELIC_LICENSE_KEY` está
  definida; sem ela a aplicação sobe sem APM, mantendo build e `docker-compose` locais funcionando
- Log forwarding do agente **desligado** (`NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false`):
  os logs JSON do stdout são coletados pelo `nri-bundle`, e ligar os dois ingeriria cada linha
  duas vezes

**A verificar com a conta ativa:** o mecanismo exato que leva `trace.id`/`span.id` do agente para
o MDC (e portanto para o JSON) não foi confirmado — depende de propriedade do agente que só dá
para validar com dados chegando.

### 6. Integração Kubernetes

`nri-bundle` via Helm: CPU e memória por pod e por node, healthchecks, e coleta dos logs de
stdout dos containers.

Atenção de capacidade: o node group é 2× `t3.medium` (2 vCPU / 4 GiB cada) e o DaemonSet mais
o `kube-state-metrics` consomem algo como 300–500m de CPU e ~1 GiB somados. Cabe, mas precisa
ser medido depois de subir, porque o HPA pode levar a aplicação a 2 réplicas em cima disso.

Egress já resolvido: a VPC tem `enable_nat_gateway = true` (`vpc.tf` em `fiap-15soat-oficina-infra-k8s`).

### 7. Dashboards e alertas em Terraform

Em `observability/`, com provider, variáveis e state próprios — nenhuma dependência de AWS:

- `newrelic.tf` — dashboard de duas páginas
- `newrelic_alertas.tf` — policy, 4 condições e notificação por e-mail
- `versions.tf` — só o provider `newrelic`, backend S3 com chave `observability/terraform.tfstate`

Alertas mínimos: taxa de erro da aplicação, falhas no processamento de OS (sobre
`oficina.integracao.falhas`), indisponibilidade do healthcheck e latência acima do limite.

### 8. Segredos

Três valores, seguindo o caminho já usado por `DB_PASSWORD` e pelas chaves JWT
(GitHub Secrets → `kubectl create secret` no job de deploy → env do container):

| Valor | Consumidor |
|---|---|
| License key | agente Java, `nri-bundle` |
| Account ID | provider Terraform |
| User API key | provider Terraform |

## Testes

- `MetricasGatewayUnitTest` não existe como conceito: a porta é uma interface. O que se testa é
  que o **use case chama a porta** — `OrdemServicoUseCaseUnitTest` ganha um mock de
  `MetricasGateway` e verifica as chamadas nas transições.
- `MicrometerMetricasGateway` testado contra um `SimpleMeterRegistry`, verificando nome,
  tags e valor dos instrumentos.
- `OrdemServicoDomainUnitTest` ganha cobertura de `statusAlteradoEm` nas transições.
- O gate JaCoCo (80% em `core.usecase`, `core.domain.entity`, `entrypoint.controller`)
  continua valendo: a porta mockável é o que mantém isso atingível.

## Fora de escopo

- Migrar a autenticação para CPF (Seção 1 do PDF)
- Separar os 4 repositórios (Seção 2)
- Diagramas, RFCs e ADRs (Seção 4) — este documento é insumo para o ADR, não o ADR
- Ferramenta de migration para o banco; o `ddl-auto=update` permanece, e a coluna nova é
  criada por ele

## Riscos

| Risco | Mitigação |
|---|---|
| Cota de 100 GB/mês consumida por log ruidoso | Desligar `show-sql` é item de escopo, não opcional |
| Região da conta escolhida errada | US, decidido antes do cadastro; irreversível depois |
| `nri-bundle` apertando os nodes | Medir após subir; `node_max_size` já é 3 |
| Só 1 usuário full platform no free tier | Definir no grupo quem fica com a cadeira e gravar o vídeo dessa conta |
| Cardinalidade de métricas | Nenhum id em tag; `motivo` restrito a classe de exceção |
