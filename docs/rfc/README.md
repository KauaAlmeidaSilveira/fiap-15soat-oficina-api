# RFCs — Request for Comments

Decisões técnicas relevantes do projeto, com motivação, alternativas consideradas e
consequências. Diferem dos ADRs por documentarem o *porquê* de uma escolha mais ampla
(nuvem, banco, estratégia de autenticação), em vez de uma decisão arquitetural pontual.

| # | Título | Status |
|---|---|---|
| [0001](0001-escolha-da-nuvem-aws.md) | Escolha da nuvem — AWS | Aceito |
| [0002](0002-escolha-do-banco-de-dados-rds-postgresql.md) | Escolha do banco de dados — Amazon RDS PostgreSQL | Aceito |

Pendente: RFC da estratégia de autenticação (CPF via Lambda) — bloqueado até decidir como a
API passa a confiar no token emitido pela Lambda (ver `CHECKLIST.md`, Seção 1).
