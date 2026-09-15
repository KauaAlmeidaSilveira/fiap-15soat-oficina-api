# ADR-0001: Uso de HPA para escalabilidade do cluster Kubernetes

- **Status:** Aceito
- **Data:** 2026-09-14

## Contexto

O Tech Challenge Fase 3 exige explicitamente um "Cluster Kubernetes com escalabilidade"
como parte da infraestrutura obrigatória (`docs/requisitos-fase3.md`). A aplicação já roda
em EKS desde a Fase 2, mas até então com um número fixo de réplicas — sem nenhum mecanismo
de autoscaling configurado.

## Decisão

Usar o **Horizontal Pod Autoscaler (HPA)** nativo do Kubernetes, escalando pelo consumo de
CPU dos pods. Configuração real em `k8s/hpa.yaml`:

- `minReplicas: 1`, `maxReplicas: 2`
- Métrica: `cpu` (Resource), alvo `averageUtilization: 70`

O HPA consulta o `metrics-server` do cluster para decidir quando escalar.

## Consequências

- O teto de 2 réplicas é baixo de propósito — reflete o node group pequeno viabilizado
  pelos créditos do AWS Academy, não uma capacidade de produção real. O objetivo aqui é
  demonstrar o mecanismo de autoscaling funcionando, não dimensionar para carga real.
- Depende do `metrics-server` estar acessível pelo control plane do EKS — já resolvido
  separadamente (a porta precisou ser liberada explicitamente, ver histórico de
  `fix(infra): liberar porta do metrics-server para o control plane do EKS`).
- Escalar por CPU é simples de operar e não exige infraestrutura adicional (nenhum
  componente extra além do que o EKS já embute), mas não reage a outros sinais de carga
  (ex.: filas, latência) — se isso vier a importar, é uma nova decisão, não uma revisão
  desta.

## Alternativas consideradas

- **Sem autoscaling** — não atende ao requisito obrigatório do desafio.
- **KEDA** — permitiria escalar por métricas customizadas (ex.: volume de OS na fila), mas
  é over-engineering para uma única métrica de CPU; adicionaria um componente extra ao
  cluster sem benefício correspondente neste estágio do projeto.
- **Scaling manual (réplicas fixas)** — não atende "escalabilidade" como requisito; exigiria
  intervenção humana para reagir a pico de carga.
