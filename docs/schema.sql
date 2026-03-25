-- ============================================================
-- DDL - Oficina Mecânica
-- Gerado a partir das entidades JPA do domínio
-- Compatível com PostgreSQL (produção)
-- ============================================================

-- -----------------------------------------------------------
-- Tabela: cliente
-- Armazena pessoas físicas (CPF) ou jurídicas (CNPJ)
-- -----------------------------------------------------------
CREATE TABLE cliente (
    id              BIGSERIAL       PRIMARY KEY,
    nome            VARCHAR(100)    NOT NULL,
    cpf_cnpj        VARCHAR(14)     NOT NULL UNIQUE,
    tipo_documento  VARCHAR(10)     NOT NULL,       -- Enum: CPF | CNPJ
    telefone        VARCHAR(20),
    email           VARCHAR(150),
    endereco        VARCHAR(255),
    criado_em       TIMESTAMP       NOT NULL,
    atualizado_em   TIMESTAMP
);

-- -----------------------------------------------------------
-- Tabela: veiculo
-- Veículos cadastrados no sistema
-- -----------------------------------------------------------
CREATE TABLE veiculo (
    id              BIGSERIAL       PRIMARY KEY,
    placa           VARCHAR(8)      NOT NULL UNIQUE,
    marca           VARCHAR(50)     NOT NULL,
    modelo          VARCHAR(80)     NOT NULL,
    ano             INTEGER         NOT NULL,
    cor             VARCHAR(20),
    chassi          VARCHAR(17),
    criado_em       TIMESTAMP       NOT NULL,
    atualizado_em   TIMESTAMP
);

-- -----------------------------------------------------------
-- Tabela: cliente_veiculo  (N:N)
-- Associação entre clientes e veículos
-- Um cliente pode ter vários veículos; um veículo pode ter vários donos
-- -----------------------------------------------------------
CREATE TABLE cliente_veiculo (
    id              BIGSERIAL   PRIMARY KEY,
    cliente_id      BIGINT      NOT NULL REFERENCES cliente(id),
    veiculo_id      BIGINT      NOT NULL REFERENCES veiculo(id),
    vinculado_em    TIMESTAMP   NOT NULL,
    CONSTRAINT uq_cliente_veiculo UNIQUE (cliente_id, veiculo_id)
);

-- -----------------------------------------------------------
-- Tabela: produto
-- Peças e serviços utilizados nas ordens de serviço
-- tipo = PECA  → possui saldo_estoque e movimentacoes
-- tipo = SERVICO → sem controle de estoque
-- -----------------------------------------------------------
CREATE TABLE produto (
    id              BIGSERIAL       PRIMARY KEY,
    nome            VARCHAR(100)    NOT NULL,
    descricao       VARCHAR(255),
    tipo            VARCHAR(10)     NOT NULL,       -- Enum: PECA | SERVICO
    preco_unitario  NUMERIC(10,2)   NOT NULL,
    unidade_medida  VARCHAR(20),
    ativo           BOOLEAN         NOT NULL DEFAULT true,
    criado_em       TIMESTAMP       NOT NULL,
    atualizado_em   TIMESTAMP
);

-- -----------------------------------------------------------
-- Tabela: saldo_estoque  (1:1 com produto)
-- Controle de quantidade disponível em estoque (somente PECA)
-- -----------------------------------------------------------
CREATE TABLE saldo_estoque (
    id              BIGSERIAL       PRIMARY KEY,
    produto_id      BIGINT          NOT NULL UNIQUE REFERENCES produto(id),
    quantidade      NUMERIC(10,3)   NOT NULL,
    atualizado_em   TIMESTAMP       NOT NULL
);

-- -----------------------------------------------------------
-- Tabela: ordem_servico
-- Ordem de Serviço aberta para um cliente/veículo
-- -----------------------------------------------------------
CREATE TABLE ordem_servico (
    id                  BIGSERIAL       PRIMARY KEY,
    numero              VARCHAR(20)     NOT NULL UNIQUE,
    cliente_id          BIGINT          NOT NULL REFERENCES cliente(id),
    veiculo_id          BIGINT          NOT NULL REFERENCES veiculo(id),
    status              VARCHAR(30)     NOT NULL DEFAULT 'RECEBIDA',
        -- Enum: RECEBIDA | EM_DIAGNOSTICO | AGUARDANDO_APROVACAO
        --       EM_EXECUCAO | FINALIZADA | ENTREGUE
    descricao_problema  VARCHAR(500),
    observacoes         VARCHAR(500),
    valor_total         NUMERIC(10,2)   DEFAULT 0.00,
    data_inicio         TIMESTAMP,
    data_fim            TIMESTAMP,
    data_entrega        TIMESTAMP,
    data_aprovacao      TIMESTAMP,
    criado_em           TIMESTAMP       NOT NULL,
    atualizado_em       TIMESTAMP,
    user_id             UUID            NOT NULL REFERENCES users(id)
);

-- -----------------------------------------------------------
-- Tabela: os_item
-- Itens (peças/serviços) vinculados a uma Ordem de Serviço
-- -----------------------------------------------------------
CREATE TABLE os_item (
    id                  BIGSERIAL       PRIMARY KEY,
    ordem_servico_id    BIGINT          NOT NULL REFERENCES ordem_servico(id),
    produto_id          BIGINT          NOT NULL REFERENCES produto(id),
    quantidade          NUMERIC(10,3)   NOT NULL,
    preco_unitario      NUMERIC(10,2)   NOT NULL,
    observacao          VARCHAR(255),
    criado_em           TIMESTAMP       NOT NULL
);

-- ============================================================
-- CAMADA DE AUTENTICAÇÃO E AUTORIZAÇÃO
-- ============================================================

-- -----------------------------------------------------------
-- Tabela: roles
-- Papéis de acesso: BASIC, ADMIN
-- -----------------------------------------------------------
CREATE TABLE roles (
    role_id     BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(50)
);

-- -----------------------------------------------------------
-- Tabela: users
-- Operadores do sistema que realizam login (≠ cliente)
-- id: UUID gerado automaticamente
-- password: hash bcrypt
-- -----------------------------------------------------------
CREATE TABLE users (
    id          UUID            PRIMARY KEY,
    username    VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL
);

-- -----------------------------------------------------------
-- Tabela: users_roles  (N:N pivot)
-- Um usuário pode ter vários papéis
-- -----------------------------------------------------------
CREATE TABLE users_roles (
    user_id     UUID    NOT NULL REFERENCES users(id),
    role_id     BIGINT  NOT NULL REFERENCES roles(role_id),
    PRIMARY KEY (user_id, role_id)
);

-- -----------------------------------------------------------
-- FK adicional em ordem_servico → users
-- user_id: usuário que criou/é responsável pela OS
-- -----------------------------------------------------------
ALTER TABLE ordem_servico
    ADD COLUMN user_id UUID NOT NULL REFERENCES users(id);

-- -----------------------------------------------------------
-- Tabela: movimentacao_estoque
-- Histórico de entradas e saídas de estoque
-- ordem_servico_id é nullable (movimentações manuais não têm OS)
-- -----------------------------------------------------------
CREATE TABLE movimentacao_estoque (
    id                  BIGSERIAL       PRIMARY KEY,
    produto_id          BIGINT          NOT NULL REFERENCES produto(id),
    tipo                VARCHAR(10)     NOT NULL,   -- Enum: ENTRADA | SAIDA
    quantidade          NUMERIC(10,3)   NOT NULL,
    motivo              VARCHAR(255),
    criado_em           TIMESTAMP       NOT NULL,
    ordem_servico_id    BIGINT          REFERENCES ordem_servico(id)  -- nullable
);
