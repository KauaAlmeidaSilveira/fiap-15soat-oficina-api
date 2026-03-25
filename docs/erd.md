---
Arquivo gerado para visualização em ferramentas que suportam Mermaid ERD.
Ferramentas compatíveis:
  - https://mermaid.live  (online, gratuito)
  - dbdiagram.io          (import via Mermaid ou DBML)
  - Notion / GitHub       (renderiza nativamente em blocos de código mermaid)
  - VS Code               (extensão "Markdown Preview Mermaid Support")
  - Draw.io               (via plugin Mermaid)
---

```mermaid
erDiagram

    cliente {
        bigint      id              PK
        varchar100  nome
        varchar14   cpf_cnpj        UK
        varchar10   tipo_documento
        varchar20   telefone
        varchar150  email
        varchar255  endereco
        timestamp   criado_em
        timestamp   atualizado_em
    }

    veiculo {
        bigint  id      PK
        varchar8    placa       UK
        varchar50   marca
        varchar80   modelo
        integer     ano
        varchar20   cor
        varchar17   chassi
        timestamp   criado_em
        timestamp   atualizado_em
    }

    cliente_veiculo {
        bigint  id          PK
        bigint  cliente_id  FK
        bigint  veiculo_id  FK
        timestamp vinculado_em
    }

    produto {
        bigint      id              PK
        varchar100  nome
        varchar255  descricao
        varchar10   tipo
        numeric     preco_unitario
        varchar20   unidade_medida
        boolean     ativo
        timestamp   criado_em
        timestamp   atualizado_em
    }

    saldo_estoque {
        bigint  id          PK
        bigint  produto_id  FK "UK"
        numeric quantidade
        timestamp atualizado_em
    }

    ordem_servico {
        bigint      id                  PK
        varchar20   numero              UK
        bigint      cliente_id          FK
        bigint      veiculo_id          FK
        uuid        user_id             FK
        varchar30   status
        varchar500  descricao_problema
        varchar500  observacoes
        numeric     valor_total
        timestamp   data_inicio
        timestamp   data_fim
        timestamp   data_entrega
        timestamp   data_aprovacao
        timestamp   criado_em
        timestamp   atualizado_em
    }

    os_item {
        bigint  id                  PK
        bigint  ordem_servico_id    FK
        bigint  produto_id          FK
        numeric quantidade
        numeric preco_unitario
        varchar255 observacao
        timestamp  criado_em
    }

    movimentacao_estoque {
        bigint  id                  PK
        bigint  produto_id          FK
        varchar10 tipo
        numeric   quantidade
        varchar255 motivo
        timestamp  criado_em
        bigint  ordem_servico_id    FK "nullable"
    }

    roles {
        bigint      role_id     PK
        varchar50   name
    }

    users {
        uuid        id          PK
        varchar255  username    UK
        varchar255  password
    }

    users_roles {
        uuid    user_id     FK
        bigint  role_id     FK
    }

    cliente           ||--o{ cliente_veiculo      : "possui veiculos"
    veiculo           ||--o{ cliente_veiculo      : "pertence a clientes"
    cliente           ||--o{ ordem_servico        : "abre"
    veiculo           ||--o{ ordem_servico        : "e atendido em"
    ordem_servico     ||--o{ os_item              : "contem"
    produto           ||--o{ os_item              : "e usado em"
    produto           ||--o| saldo_estoque        : "tem saldo"
    produto           ||--o{ movimentacao_estoque : "gera movimentacoes"
    ordem_servico     ||--o{ movimentacao_estoque : "origina saidas"
    users             ||--o{ users_roles          : "tem papeis"
    roles             ||--o{ users_roles          : "atribuido a"
    users             ||--o{ ordem_servico        : "cria"
```
