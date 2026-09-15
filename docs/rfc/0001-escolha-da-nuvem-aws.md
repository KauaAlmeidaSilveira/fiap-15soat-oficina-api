# RFC-0001: Escolha da nuvem — AWS

- **Status:** Aceito
- **Data:** 2026-09-14

## Resumo

O projeto usa **AWS** como provedor de nuvem para a infraestrutura implementada até agora:
EKS (cluster Kubernetes) e RDS (banco gerenciado).

## Motivação / Contexto

O fator mais concreto por trás da escolha foi o **AWS Academy**, programa educacional que a
FIAP disponibiliza para os alunos — os créditos usados neste projeto vêm dele. Isso não é
um detalhe menor: sem esses créditos, o time não teria orçamento próprio para manter EKS +
RDS rodando durante todo o desenvolvimento e avaliação.

Dito isso, a escolha também se sustenta em motivos que valeriam independentemente do
crédito educacional:

- **Volume de conteúdo e comunidade**: para um time estudantil, a quantidade de
  tutoriais, respostas de fórum e documentação oficial sobre AWS reduz o tempo gasto
  destravando problemas de infraestrutura — o que sobra é tempo para a aplicação em si.
- **Cobertura nativa a partir de um único provedor**: EKS (Kubernetes gerenciado) e RDS
  (banco gerenciado) resolvem, sozinhos, o cluster e o banco sem precisar compor múltiplos
  vendors — um catálogo mais estreito exigiria integrar serviços de nuvens diferentes.
- **Adoção de mercado**: é a nuvem mais usada no mercado de trabalho brasileiro atualmente,
  o que dá valor de aprendizado além da nota do desafio.

## Alternativas consideradas

- **Azure** e **GCP** — ambas cobririam os mesmos requisitos técnicos já implementados
  (AKS/GKE como equivalente ao EKS, bancos gerenciados equivalentes ao RDS). Foram
  descartadas principalmente por não haver, para este grupo, um programa de créditos
  educacionais equivalente ao AWS Academy — o custo real de manter a infraestrutura no ar
  durante o desafio pesou mais do que uma comparação técnica fina entre os três provedores.

## Consequências

- Dependência de **credenciais temporárias** do AWS Academy: o workflow de CI/CD usa
  `AWS_SESSION_TOKEN`, que expira e precisa ser renovado manualmente — tanto localmente
  quanto nos GitHub Secrets dos repositórios de infraestrutura (ver `CHECKLIST.md`,
  ponto de atenção nº 6).
- Portabilidade reduzida fora do contexto acadêmico: o desenho atual assume os limites de
  conta do Academy (node group pequeno, restrições de serviço) — levar isso para produção
  real exigiria revisitar dimensionamento e, possivelmente, tipo de conta AWS.
- Implementado: os três repositórios de infraestrutura (`infra-k8s`, `infra-db`,
  `observability`) já provisionam recursos reais na conta AWS do grupo.
