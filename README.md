# Oficina Mecânica — Sistema Integrado de Atendimento

> Tech Challenge — FIAP SOAT Fase 1
> Back-end monolítico com Spring Boot 3.2 · Java 21 · PostgreSQL / H2

---

## Sobre o Projeto

Sistema back-end para uma oficina mecânica de médio porte, com gestão completa de ordens de serviço, clientes, veículos, peças, serviços e controle de estoque.

### Decisão de banco de dados

O projeto utiliza **dois perfis**:

| Perfil | Banco | Quando usar |
|--------|-------|-------------|
| `dev` (padrão local) | H2 in-memory | Desenvolvimento e testes — zero configuração, console web integrado |
| `default` (Docker/produção) | PostgreSQL 16 | Ambiente containerizado via `docker-compose` |

A troca é transparente: basta definir `SPRING_PROFILES_ACTIVE` na variável de ambiente. O Hibernate gera o DDL automaticamente em ambos os casos.

---

## ⚠️ Versão MVP — Sem autenticação

Nesta fase todos os endpoints são **públicos** (sem JWT). A autenticação será implementada em fase futura.

---

## Arquitetura

O projeto segue arquitetura em camadas (monolito):

```
br.com.fiap.oficina
├── controller/          # Controllers REST — entrada HTTP
├── service/             # Regras de negócio e casos de uso
├── domain/
│   ├── model/           # Entidades JPA (domínio)
│   ├── enums/           # StatusOS, TipoProduto, TipoDocumento, TipoMovimentacao
│   └── repository/      # Interfaces Spring Data JPA
├── dto/
│   ├── request/         # Records de entrada com validação (@Valid)
│   └── response/        # Records de saída
├── handler/             # GlobalExceptionHandler + exceções de domínio
└── config/              # CorsConfig, SwaggerConfig, DataLoader
```

### Entidades do domínio

| Entidade | Descrição |
|---|---|
| `Cliente` | Pessoa física (CPF) ou jurídica (CNPJ) |
| `Veiculo` | Veículo com placa, marca, modelo, ano, cor e chassi |
| `ClienteVeiculo` | Vínculo N:N entre cliente e veículo |
| `OrdemServico` | OS com status, itens e valor total calculado |
| `OsItem` | Item da OS (produto + quantidade + preço unitário) |
| `Produto` | Peça (`PECA`) ou serviço (`SERVICO`) |
| `MovimentacaoEstoque` | Registro de cada entrada ou saída de estoque |
| `SaldoEstoque` | Saldo atual de cada peça |

### Fluxo de status da OS

```
RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → EM_EXECUCAO → FINALIZADA → ENTREGUE
```

---

## Como executar

### Pré-requisitos
- Java 21+
- Maven 3.9+
- Docker e Docker Compose (para execução em container)

### Execução local (perfil `dev` com H2)

```bash
# 1. Clone o repositório
git clone <url-do-repositorio>
cd oficina

# 2. Execute com Maven
mvn spring-boot:run

# A aplicação sobe em: http://localhost:8080
# Perfil ativo: dev (H2 in-memory)
```

### Execução com Docker (PostgreSQL)

```bash
docker-compose up --build
```

O `docker-compose` sobe dois containers: **postgres** (PostgreSQL 16) e **oficina-api** (Spring Boot). A API aguarda o banco estar saudável antes de iniciar.

---

## Documentação da API (Swagger)

Após subir a aplicação, acesse:

```
http://localhost:8080/swagger-ui.html
```

A documentação completa de todos os endpoints está disponível via OpenAPI 3 (`/v3/api-docs`).

---

## Console H2 (apenas perfil `dev`)

```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:oficina
Usuário:  sa
Senha:    (vazio)
```

---

## Testes

```bash
# Executar todos os testes (unitários + integração)
mvn test

# Executar com relatório de cobertura JaCoCo
mvn verify

# Relatório HTML gerado em:
# target/site/jacoco/index.html
```

### Cobertura atual

| Pacote | Cobertura |
|--------|-----------|
| `controller` | 93,8% |
| `service` | 87,0% |
| `domain.model` | 100% |
| `handler` | 96,9% |

Cobertura mínima exigida pelo gate do JaCoCo: **80%**.

### Tipos de testes

| Classe | Tipo |
|--------|------|
| `*ServiceTest` | Testes unitários com Mockito |
| `*ControllerIT` | Testes de integração com `@SpringBootTest` + MockMvc |
| `Ordem/OsItem/SaldoEstoqueTest` | Testes de domínio |
| `GlobalExceptionHandlerTest` | Testes do handler de erros |

---

## Endpoints

### Clientes — `/api/clientes`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/clientes` | Cadastrar cliente |
| GET | `/api/clientes` | Listar todos |
| GET | `/api/clientes/{id}` | Buscar por ID |
| GET | `/api/clientes/cpf-cnpj/{cpfCnpj}` | Buscar por CPF/CNPJ |
| PUT | `/api/clientes/{id}` | Atualizar |
| DELETE | `/api/clientes/{id}` | Deletar |

### Veículos — `/api/veiculos`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/veiculos` | Cadastrar veículo |
| GET | `/api/veiculos` | Listar todos |
| GET | `/api/veiculos/{id}` | Buscar por ID |
| GET | `/api/veiculos/placa/{placa}` | Buscar por placa |
| PUT | `/api/veiculos/{id}` | Atualizar |
| DELETE | `/api/veiculos/{id}` | Deletar |
| POST | `/api/veiculos/{veiculoId}/clientes/{clienteId}` | Vincular cliente ao veículo |
| GET | `/api/veiculos/cliente/{clienteId}` | Listar veículos de um cliente |

### Ordens de Serviço — `/api/ordens-servico`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/ordens-servico` | Criar OS |
| GET | `/api/ordens-servico` | Listar todas (filtro opcional: `?status=` ou `?clienteId=`) |
| GET | `/api/ordens-servico/{id}` | Detalhar OS |
| GET | `/api/ordens-servico/numero/{numero}` | Consulta pública por número (cliente) |
| PATCH | `/api/ordens-servico/{id}/avancar-status` | Avançar status da OS |
| PATCH | `/api/ordens-servico/{id}/aprovar` | Cliente aprova orçamento |
| POST | `/api/ordens-servico/{id}/itens` | Adicionar item à OS |
| GET | `/api/ordens-servico/metricas/tempo-medio` | Tempo médio de execução (horas) |

### Produtos e Estoque — `/api/produtos`

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/produtos` | Cadastrar produto (peça ou serviço) |
| GET | `/api/produtos` | Listar todos (filtro opcional: `?tipo=PECA` ou `?tipo=SERVICO`) |
| GET | `/api/produtos/{id}` | Buscar por ID |
| PUT | `/api/produtos/{id}` | Atualizar produto |
| DELETE | `/api/produtos/{id}` | Inativar produto |
| POST | `/api/produtos/estoque/movimentacao` | Registrar entrada ou saída de estoque |
| GET | `/api/produtos/{id}/estoque/movimentacoes` | Histórico de movimentações de um produto |
| GET | `/api/produtos/estoque/movimentacoes` | Histórico de todas as movimentações |

---

## Estrutura do projeto

```
oficina/
├── src/
│   ├── main/
│   │   ├── java/br/com/fiap/oficina/
│   │   └── resources/
│   │       ├── application.properties        # Perfil padrão (PostgreSQL)
│   │       └── application-dev.properties    # Perfil dev (H2)
│   └── test/
│       └── java/br/com/fiap/oficina/
│           ├── api/        # Testes de integração (*IT)
│           ├── application/# Testes unitários de serviço
│           ├── domain/     # Testes de domínio
│           └── handler/    # Testes do exception handler
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

### Stack tecnológica

| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Java | 21 | Linguagem |
| Spring Boot | 3.2.5 | Framework principal |
| Spring Data JPA | — | Persistência |
| Spring Validation | — | Validação de DTOs |
| PostgreSQL | 16 | Banco em produção |
| H2 | — | Banco em desenvolvimento/testes |
| springdoc-openapi | 2.5.0 | Swagger UI / OpenAPI 3 |
| Lombok | — | Redução de boilerplate |
| MapStruct | 1.5.5 | Mapeamento de DTOs |
| JaCoCo | 0.8.11 | Cobertura de testes |
| JUnit 5 + Mockito | — | Testes |
