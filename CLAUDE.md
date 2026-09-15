# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Pré-requisito obrigatório: chaves JWT

`SecurityConfig` injeta `RSAPublicKey`/`RSAPrivateKey` direto de `classpath:jwt.*.key`. Sem esses arquivos **nenhum teste de integração e nenhuma execução local sobe** (falha no boot do contexto Spring). Gere antes de qualquer `mvn test`/`mvn verify`/`spring-boot:run`:

```bash
openssl genrsa -out src/main/resources/jwt.private.key 2048
openssl rsa -in src/main/resources/jwt.private.key -pubout -out src/main/resources/jwt.public.key
```

As chaves em `src/main/resources/` são gitignoradas. **Atenção:** chaves na raiz do repositório (`./jwt.private.key`, `./jwt.public.key`) **não** estão no `.gitignore` — se existirem ali, nunca as adicione ao commit.

## Comandos

```bash
# Rodar local (perfil dev, H2 in-memory, console em /h2-console)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Todos os testes (unitários + integração; surefire inclui *Test, *Tests e *IT)
mvn test

# Build completo + gate de cobertura JaCoCo (o que a CI roda)
mvn verify

# Uma classe de teste
mvn test -Dtest=OrdemServicoUseCaseUnitTest

# Um método
mvn test -Dtest=ClienteControllerIT#deveCriarCliente

# Docker (PostgreSQL 16 + API, perfil default)
cp .env.example .env && docker-compose up --build
```

Relatório de cobertura: `target/site/jacoco/index.html`. Swagger: `http://localhost:8080/swagger-ui.html`.

O **gate JaCoCo roda na fase `verify`** e exige 80% de cobertura de instruções nos pacotes `core.usecase`, `core.domain.entity` e `entrypoint.controller`. Código novo nesses três pacotes sem teste quebra o build da CI.

Não há Maven Wrapper (`mvnw`) — use o `mvn` do sistema (Maven 3.9+, Java 21).

## Arquitetura

Clean Architecture. A regra de dependência é o que governa tudo: `entrypoint` e `dataprovider` dependem de `core`; **`core` não conhece nenhum dos dois, nem Spring, nem JPA, nem HTTP**.

```
entrypoint/controller  →  core/usecase  →  core/gateway (interfaces)  ←  dataprovider/*
       ↕ DtoMapper                                                          ↕ PersistenceMapper
   dto/request|response                                            persistence/entity (JPA)
```

Consequências práticas que não são óbvias ao ler um arquivo só:

- **Use cases são POJOs**, sem `@Service`/`@Component`. São instanciados à mão em `config/UseCaseConfig.java` — ao criar um use case novo, registre o `@Bean` lá ou ele não existe no contexto.
- **Core não usa slf4j/Lombok `@Slf4j`**: `OrdemServicoUseCase` loga com `System.Logger` do JDK justamente para não trazer dependência de framework para dentro do núcleo.
- **Transações vivem nos gateways**, não nos use cases. Cada método de `*GatewayImpl` em `dataprovider/gateway` carrega seu `@Transactional`/`@Transactional(readOnly = true)`.
- **Existem dois conjuntos de classes com os mesmos nomes**: `core.domain.entity.Cliente` (domínio puro, Lombok `@Builder`) e `dataprovider.persistence.entity.Cliente` (JPA). Por isso os mappers de persistência usam nomes totalmente qualificados nas assinaturas (`br.com.fiap.oficina.dataprovider.persistence.entity.Cliente`). Ao mexer nessa camada, confira sempre qual dos dois está sendo importado.
- **Duas camadas de MapStruct** (`componentModel = "spring"`, geradas em `target/generated-sources`): `dataprovider/persistence/mapper` (JPA ↔ domínio, incluindo `updateEntity` com `@MappingTarget` para updates parciais) e `entrypoint/controller/mapper` (DTO ↔ domínio).
- **Gateways de saída não-JPA** também são portas do core: `CriptografiaSenhaGateway` (BCrypt), `TokenAprovacaoGateway` (JWT do link de aprovação) e `NotificacaoAprovacaoGateway` (e-mail).

### Perfis Spring — trocam mais que o banco

| Perfil | Banco | Notificação de aprovação | DataLoader |
|---|---|---|---|
| `dev` | H2 (`create-drop`) | `NotificacaoAprovacaoLogService` (só loga) | roda |
| `test` (usado pelos testes) | H2 `oficina_test` | `NotificacaoAprovacaoLogService` | **não** roda (`@Profile("!test")`) |
| `default` (Docker/K8s/prod) | PostgreSQL (`ddl-auto=update`) | `NotificacaoAprovacaoSmtpService` (SMTP Gmail, `@Async`) | roda |

A troca de implementação de e-mail é feita por `@Profile` nas duas classes de `dataprovider/notificacao` — não há property de feature flag. Se criar um terceiro perfil, lembre-se de que o core resolve `NotificacaoAprovacaoGateway` por injeção e **exatamente uma** implementação precisa estar ativa.

Não há ferramenta de migration (Flyway/Liquibase): o schema é gerado pelo Hibernate via `ddl-auto` em todos os ambientes, inclusive produção.

## Segurança e autorização

- Autenticação: **a API não emite tokens de sessão.** O login (funcionário por e-mail + senha, cliente por CPF + senha) é da Lambda em `fiap-15soat-oficina-lambda-auth`, atrás do API Gateway. A API só valida: RS256 com o par RSA acima, `aud = oficina-api` e `iss = jwt.issuer` (`JWT_ISSUER`, a URL do Gateway), em `SecurityConfig.sessaoJwtDecoder`. Roles vêm da claim `roles`, com prefixo `ROLE_`.
- Autorização em duas camadas: **a URL separa cliente de funcionário** (`/api/minhas-ordens-servico/**` → `CLIENTE`; resto de `/api/**` → `ADMIN`/`RECEPCAO`/`OPERADOR`, no `SecurityConfig`), e **`@PreAuthorize` separa perfis de funcionário** entre si. Rota nova de funcionário já nasce fechada para clientes; rota de cliente precisa ficar sob `/api/minhas-ordens-servico/` e tirar o `clienteId` do `sub` do token, nunca de parâmetro.
- `cliente.senha_hash` existe só na entidade JPA (ignorada nos mappers); quem lê e escreve é a Lambda.
- O endpoint público `GET /aprovacao-os?token=` reaproveita o mesmo par de chaves RSA para um JWT curto de aprovação/recusa de orçamento. Não há coluna de "token usado": a idempotência vem do próprio `StatusOS` (segunda tentativa cai em 409).

## Tratamento de erros

`GlobalExceptionHandler` (`@RestControllerAdvice`) converte exceções de domínio em `ProblemDetail` (RFC 7807). Lance a exceção de domínio certa em vez de montar `ResponseEntity` de erro no controller:

| Exceção | HTTP |
|---|---|
| `RecursoNaoEncontradoException` | 404 |
| `RegraDeNegocioException` | 422 |
| `IllegalStateException` | 409 |
| `CredenciaisInvalidasException` | 401 |
| `AccessDeniedException` | 403 |
| `MethodArgumentNotValidException` (`@Valid`) | 400, com mapa `campos` |

## Regras de negócio centrais

- **Máquina de estados da OS** vive em `core/domain/entity/OrdemServico.avancarStatus()` / `aprovar()` — é a entidade de domínio que decide, não o use case. Fluxo: `RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → EM_EXECUCAO → FINALIZADA → ENTREGUE`, com desvio para `REPROVADA` na recusa.
- **Baixa de estoque** acontece ao sair de `AGUARDANDO_APROVACAO` (por `avancarStatus` ou por `aprovar(true)`), só para itens do tipo `PECA`. Remover item de OS já aprovada gera movimentação de `ENTRADA` (estorno).
- **Saldo de estoque**: `MovimentacaoEstoque` é a fonte da verdade (soma `ENTRADA − SAIDA`); `SaldoEstoque` é cache denormalizado, atualizado por `EstoqueGateway.sincronizarSaldo(produtoId)`. Toda movimentação nova precisa ser seguida de `sincronizarSaldo`, ou o saldo lido fica defasado.
- **Listagem padrão de OS** (`GET /api/ordens-servico` sem filtro) esconde `FINALIZADA`/`ENTREGUE`/`REPROVADA` e ordena por prioridade de status (mapa `PRIORIDADE_LISTAGEM` no use case) e depois por `criadoEm` ascendente. Os filtros `?status=` e `?clienteId=` ignoram essa exclusão.
- A notificação de aprovação é *best-effort*: `notificarAprovacaoPendente` engole exceções e só loga um warning — falha de SMTP nunca derruba a transição de status.

## Testes

- `*UnitTest` em `core/usecase` e `core/domain/entity`: JUnit 5 + Mockito puro (`@ExtendWith(MockitoExtension.class)`), use case instanciado no `@BeforeEach` com gateways mockados. Assertions com AssertJ.
- `*ControllerIT` em `test/.../controller`: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@WithMockUser(roles = {...})` para autorização, e `@DirtiesContext(AFTER_EACH_TEST_METHOD)` porque o H2 é compartilhado dentro da classe. Não há Testcontainers.
- Não existe failsafe: os `*IT` rodam junto no `mvn test` via configuração do surefire no `pom.xml`.

## Convenções

- Código, nomes de domínio, mensagens de erro, logs e commits em **português**; commits seguem Conventional Commits (`feat:`, `fix(infra):`, `refactor:`, `docs:`…).
- Sem comentários desnecessários no código Java — comente só o não-óbvio (ex.: o comentário sobre `open-session-in-view` em `EstoqueGatewayImpl.criarSaldoInicial`).
- DTOs são `record`s em `dto/request` e `dto/response`; validação com Bean Validation, incluindo o `@CpfOuCnpj` customizado em `validation/`.

## Deploy

Este repositório é um de quatro: a aplicação (aqui), `fiap-15soat-oficina-infra-k8s`,
`fiap-15soat-oficina-infra-db` e `fiap-15soat-oficina-lambda-auth`. Os dashboards e alertas do
New Relic ficam aqui em `observability/` — as queries NRQL citam por string os nomes das métricas
de `MicrometerMetricasGateway`, então rename de métrica e ajuste de dashboard cabem no mesmo PR.


CI/CD em `.github/workflows/ci-cd.yml`: `mvn verify` → build/push da imagem no ECR (tags: SHA curto + `latest`) → `kubectl apply` no EKS, substituindo por `sed` os placeholders `<RDS_ENDPOINT>`, `<APP_PUBLIC_BASE_URL>` e `<ECR_URI>` nos manifestos de `k8s/`. O pipeline **não** provisiona infraestrutura — ela vive em repositórios separados: `fiap-15soat-oficina-infra-k8s` (VPC, EKS, ECR) e `fiap-15soat-oficina-infra-db` (RDS). Detalhes em `k8s/README.md`; a lista de secrets do GitHub está no `README.md`.

Em Kubernetes as chaves JWT vêm de um `Secret` montado em arquivo (mesmo par para todas as réplicas) — chaves geradas por pod invalidariam tokens emitidos por outra réplica.
