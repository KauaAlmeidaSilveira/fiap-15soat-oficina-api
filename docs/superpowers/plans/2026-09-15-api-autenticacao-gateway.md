# API Java — Autenticação via Gateway: Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preparar a API para receber tokens emitidos pela Lambda atrás do API Gateway: rotas de "minhas OS" para o cliente, bloqueio de clientes nas rotas de funcionário, validação de `aud`/`iss` e remoção do login local.

**Architecture:** A API deixa de emitir tokens de sessão e passa só a validá-los (RS256, `aud = oficina-api`, `iss = JWT_ISSUER`). Uma regra de URL no `SecurityConfig` separa cliente de funcionário; os `@PreAuthorize` existentes continuam separando perfis de funcionário. A coluna `cliente.senha_hash` nasce aqui (Hibernate `ddl-auto`), mas só a Lambda a lê e escreve.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Spring Security OAuth2 Resource Server, MapStruct, JUnit 5, Mockito, AssertJ, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-15-api-gateway-lambda-auth-design.md`

**Plano irmão:** `docs/superpowers/plans/2026-09-15-lambda-auth.md` (repositório `fiap-15soat-oficina-lambda-auth`). Este plano não depende dele para passar nos testes.

## Global Constraints

- Pré-requisito de qualquer `mvn test`: chaves em `src/main/resources/jwt.private.key` e `jwt.public.key` (ver `CLAUDE.md`). Nunca commitar chaves.
- Maven do sistema (`mvn`), sem wrapper. Java 21: nesta máquina o `mvn` roda por padrão no JDK 17, então exporte antes de qualquer comando `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` e confira com `mvn -v`.
- `core/` sem Spring, JPA, HTTP ou slf4j. Use cases são POJOs registrados em `config/UseCaseConfig.java`.
- Código, mensagens de erro, nomes de teste e `@DisplayName` em português.
- Sem comentários desnecessários em código Java.
- Commits em **inglês**, Conventional Commits, **só a linha de assunto** — sem corpo e sem linha de coautoria.
- Gate JaCoCo de 80% em `core.usecase`, `core.domain.entity` e `entrypoint.controller` (`mvn verify`).
- Audiência dos tokens de sessão: `oficina-api`. Emissor do token de aprovação: `back-end`.
- Role do cliente: `CLIENTE`. Roles de funcionário: `ADMIN`, `RECEPCAO`, `OPERADOR`.
- Um checkpoint por tarefa: testes da tarefa passando + `mvn test` verde + commit, antes de seguir.

---

### Task 0: Branch de trabalho

- [ ] **Step 1: Criar a branch a partir do `master` atualizado**

```bash
git checkout master && git pull
git checkout -b feat/api-gateway-auth
```

- [ ] **Step 2: Confirmar a linha de base verde**

Run: `mvn test`
Expected: `BUILD SUCCESS`. Se falhar aqui, pare: o problema é anterior a este plano.

---

### Task 1: Coluna `senha_hash` preservada nas atualizações de cliente

**Files:**
- Modify: `src/main/java/br/com/fiap/oficina/dataprovider/persistence/entity/Cliente.java`
- Modify: `src/main/java/br/com/fiap/oficina/dataprovider/persistence/mapper/ClientePersistenceMapper.java`
- Create: `src/test/java/br/com/fiap/oficina/dataprovider/gateway/ClienteGatewayImplIT.java`

**Interfaces:**
- Produces: coluna `cliente.senha_hash VARCHAR(60) NULL`, consumida pela Lambda (`UPDATE ... SET senha_hash`). A entidade JPA expõe `getSenhaHash()`/`setSenhaHash(String)`. O `Cliente` de domínio **não** muda.

- [ ] **Step 1: Escrever o teste de integração**

```java
package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClienteGatewayImplIT {

    private static final String HASH_DEFINIDO_PELA_LAMBDA = "$2a$10$hashDefinidoPelaLambdaNoPrimeiroAcesso1234567890123";

    @Autowired ClienteGateway clienteGateway;
    @Autowired ClienteRepository clienteRepository;

    @Test
    @DisplayName("Atualizar o cadastro do cliente deve preservar a senha definida no primeiro acesso")
    void atualizarClientePreservaSenhaHash() {
        Cliente salvo = clienteGateway.salvar(Cliente.builder()
                .nome("Carlos Mendes")
                .cpfCnpj("52998224725")
                .tipoDocumento(TipoDocumento.CPF)
                .email("carlos@email.com")
                .build());

        var entidade = clienteRepository.findById(salvo.getId()).orElseThrow();
        entidade.setSenhaHash(HASH_DEFINIDO_PELA_LAMBDA);
        clienteRepository.save(entidade);

        clienteGateway.salvar(Cliente.builder()
                .id(salvo.getId())
                .nome("Carlos Mendes Filho")
                .cpfCnpj("52998224725")
                .tipoDocumento(TipoDocumento.CPF)
                .email("carlos@email.com")
                .status(salvo.getStatus())
                .build());

        var recarregado = clienteRepository.findById(salvo.getId()).orElseThrow();
        assertThat(recarregado.getNome()).isEqualTo("Carlos Mendes Filho");
        assertThat(recarregado.getSenhaHash()).isEqualTo(HASH_DEFINIDO_PELA_LAMBDA);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=ClienteGatewayImplIT`
Expected: FAIL de compilação — `cannot find symbol: method setSenhaHash(java.lang.String)`.

- [ ] **Step 3: Adicionar o campo à entidade JPA**

Em `dataprovider/persistence/entity/Cliente.java`, logo após o campo `status`:

```java
    @Column(name = "senha_hash", length = 60)
    private String senhaHash;
```

- [ ] **Step 4: Ignorar o campo nos dois mapeamentos para entidade**

Substitua o corpo de `ClientePersistenceMapper` por:

```java
@Mapper(componentModel = "spring")
public interface ClientePersistenceMapper {

    Cliente toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.Cliente entity);

    @Mapping(target = "veiculos", ignore = true)
    @Mapping(target = "ordensServico", ignore = true)
    @Mapping(target = "senhaHash", ignore = true)
    br.com.fiap.oficina.dataprovider.persistence.entity.Cliente toEntity(Cliente domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "veiculos", ignore = true)
    @Mapping(target = "ordensServico", ignore = true)
    @Mapping(target = "senhaHash", ignore = true)
    void updateEntity(@MappingTarget br.com.fiap.oficina.dataprovider.persistence.entity.Cliente entity, Cliente domain);
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn test -Dtest=ClienteGatewayImplIT`
Expected: PASS. Confira também que o build não emite `Unmapped target property: "senhaHash"`.

- [ ] **Step 6: Suíte completa**

Run: `mvn test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/fiap/oficina/dataprovider/persistence/entity/Cliente.java \
        src/main/java/br/com/fiap/oficina/dataprovider/persistence/mapper/ClientePersistenceMapper.java \
        src/test/java/br/com/fiap/oficina/dataprovider/gateway/ClienteGatewayImplIT.java
git commit -m "feat(cliente): add senha_hash column preserved on updates"
```

---

### Task 2: Use case `buscarDoCliente`

**Files:**
- Modify: `src/main/java/br/com/fiap/oficina/core/usecase/OrdemServicoUseCase.java`
- Modify: `src/test/java/br/com/fiap/oficina/core/usecase/OrdemServicoUseCaseUnitTest.java`

**Interfaces:**
- Consumes: `OrdemServicoGateway.buscarPorId(Long): Optional<OrdemServico>` (existente).
- Produces: `public OrdemServico buscarDoCliente(Long osId, Long clienteId)` — devolve a OS se `os.getCliente().getId()` for igual a `clienteId`; senão lança `RecursoNaoEncontradoException("Ordem de Serviço", osId)`. Usado pela Task 5.

- [ ] **Step 1: Escrever os testes**

Adicione ao final de `OrdemServicoUseCaseUnitTest` (antes do `}` final da classe). O `setup()` existente já cria `os` com `id = 1L` e `cliente.id = 1L`:

```java
    @Test
    @DisplayName("Deve devolver a OS quando ela pertence ao cliente autenticado")
    void buscarDoClienteDevolveOsDoProprioCliente() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));

        assertThat(useCase.buscarDoCliente(1L, 1L)).isSameAs(os);
    }

    @Test
    @DisplayName("Deve responder como inexistente a OS de outro cliente")
    void buscarDoClienteNaoRevelaOsDeOutroCliente() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> useCase.buscarDoCliente(1L, 2L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando a OS não existe")
    void buscarDoClienteOsInexistente() {
        when(osGateway.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.buscarDoCliente(99L, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=OrdemServicoUseCaseUnitTest`
Expected: FAIL de compilação — `cannot find symbol: method buscarDoCliente(long,long)`.

- [ ] **Step 3: Implementar**

Em `OrdemServicoUseCase`, logo após `buscarPorNumero`:

```java
    public OrdemServico buscarDoCliente(Long osId, Long clienteId) {
        return osGateway.buscarPorId(osId)
                .filter(os -> os.getCliente() != null && clienteId.equals(os.getCliente().getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", osId));
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=OrdemServicoUseCaseUnitTest`
Expected: PASS em todos os testes da classe.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/fiap/oficina/core/usecase/OrdemServicoUseCase.java \
        src/test/java/br/com/fiap/oficina/core/usecase/OrdemServicoUseCaseUnitTest.java
git commit -m "feat(os): add owner-scoped service order lookup"
```

---

### Task 3: Tokens de sessão e de aprovação não valem um pelo outro

**Files:**
- Modify: `src/main/java/br/com/fiap/oficina/config/SecurityConfig.java`
- Modify: `src/main/java/br/com/fiap/oficina/dataprovider/token/AprovacaoTokenGatewayImpl.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application.properties`
- Create: `src/test/java/br/com/fiap/oficina/config/SessaoJwtDecoderUnitTest.java`
- Modify: `src/test/java/br/com/fiap/oficina/dataprovider/token/AprovacaoTokenGatewayImplUnitTest.java`

**Interfaces:**
- Produces: `static JwtDecoder SecurityConfig.sessaoJwtDecoder(RSAPublicKey publicKey, String issuer)` (package-private) e `static final String SecurityConfig.AUDIENCIA_SESSAO = "oficina-api"`. Propriedade `jwt.issuer` (env `JWT_ISSUER`), consumida pelo ConfigMap na Task 6.
- O bean `JwtDecoder jwtDecoder()` continua existindo, sem validadores extras, e é usado **só** pelo `AprovacaoTokenGatewayImpl`. O resource server passa a usar o decoder de sessão explicitamente.

- [ ] **Step 1: Escrever o teste do decoder de sessão**

```java
package br.com.fiap.oficina.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessaoJwtDecoderUnitTest {

    private static final String EMISSOR = "https://abc123.execute-api.us-east-1.amazonaws.com";

    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair par = generator.generateKeyPair();
        RSAPublicKey publica = (RSAPublicKey) par.getPublic();
        JWK jwk = new RSAKey.Builder(publica).privateKey((RSAPrivateKey) par.getPrivate()).build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        decoder = SecurityConfig.sessaoJwtDecoder(publica, EMISSOR);
    }

    private String token(Consumer<JwtClaimsSet.Builder> ajuste) {
        Instant agora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(EMISSOR)
                .audience(List.of(SecurityConfig.AUDIENCIA_SESSAO))
                .subject("kaua@gmail.com")
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(3600))
                .claim("roles", List.of("ADMIN"));
        ajuste.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    @Test
    @DisplayName("Deve aceitar token de sessão com emissor e audiência esperados")
    void aceitaTokenDeSessaoValido() {
        assertThat(decoder.decode(token(c -> {})).getSubject()).isEqualTo("kaua@gmail.com");
    }

    @Test
    @DisplayName("Deve rejeitar token sem audiência")
    void rejeitaTokenSemAudiencia() {
        String semAudiencia = token(c -> c.claims(m -> m.remove("aud")));

        assertThatThrownBy(() -> decoder.decode(semAudiencia)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Deve rejeitar token com audiência de outro sistema")
    void rejeitaTokenComOutraAudiencia() {
        String outraAudiencia = token(c -> c.audience(List.of("outro-sistema")));

        assertThatThrownBy(() -> decoder.decode(outraAudiencia)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Deve rejeitar token de outro emissor")
    void rejeitaTokenDeOutroEmissor() {
        String outroEmissor = token(c -> c.issuer("https://emissor-falso.example.com"));

        assertThatThrownBy(() -> decoder.decode(outroEmissor)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Deve rejeitar o token do link de aprovação usado como token de sessão")
    void rejeitaTokenDeAprovacao() {
        String aprovacao = token(c -> c.issuer("back-end").subject("os-aprovacao")
                .claims(m -> m.remove("aud")).claim("osId", "1").claim("aprovado", true));

        assertThatThrownBy(() -> decoder.decode(aprovacao)).isInstanceOf(JwtException.class);
    }
}
```

- [ ] **Step 2: Adicionar os testes de rejeição no gateway de aprovação**

Em `AprovacaoTokenGatewayImplUnitTest`: transforme o `jwtEncoder` local do `setup()` em campo e acrescente dois testes.

Declare o campo junto de `gateway`:

```java
    private JwtEncoder jwtEncoder;
```

No `setup()`, troque a linha `JwtEncoder jwtEncoder = new NimbusJwtEncoder(...)` por:

```java
        jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
```

Adicione os imports `org.springframework.security.oauth2.jwt.JwtClaimsSet`, `org.springframework.security.oauth2.jwt.JwtEncoderParameters` e `java.util.List`, e os testes ao final da classe:

```java
    @Test
    @DisplayName("Deve rejeitar token de sessão (com audiência) usado como link de aprovação")
    void deveRejeitarTokenComAudiencia() {
        Instant agora = Instant.now();
        String tokenSessao = jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("back-end")
                .audience(List.of("oficina-api"))
                .subject("os-aprovacao")
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(3600))
                .claim("osId", "1")
                .claim("aprovado", true)
                .build())).getTokenValue();

        assertThatThrownBy(() -> gateway.validar(tokenSessao))
                .isInstanceOf(TokenAprovacaoInvalidoException.class);
    }

    @Test
    @DisplayName("Deve rejeitar token de aprovação de outro emissor")
    void deveRejeitarTokenDeOutroEmissor() {
        Instant agora = Instant.now();
        String outroEmissor = jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("https://abc123.execute-api.us-east-1.amazonaws.com")
                .subject("os-aprovacao")
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(3600))
                .claim("osId", "1")
                .claim("aprovado", true)
                .build())).getTokenValue();

        assertThatThrownBy(() -> gateway.validar(outroEmissor))
                .isInstanceOf(TokenAprovacaoInvalidoException.class);
    }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn test -Dtest='SessaoJwtDecoderUnitTest,AprovacaoTokenGatewayImplUnitTest'`
Expected: FAIL de compilação em `SessaoJwtDecoderUnitTest` (`sessaoJwtDecoder` e `AUDIENCIA_SESSAO` não existem). Depois do Step 4, os dois testes novos do gateway de aprovação ainda falham até o Step 5.

- [ ] **Step 4: Implementar o decoder de sessão no `SecurityConfig`**

Adicione os imports:

```java
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.List;
```

Adicione a constante e a propriedade junto dos campos existentes:

```java
    static final String AUDIENCIA_SESSAO = "oficina-api";

    @Value("${jwt.issuer}")
    private String issuer;
```

Troque a linha do resource server em `devFilterChain` por:

```java
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(sessaoJwtDecoder(this.publicKey, this.issuer))
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
```

E adicione o método estático logo após `jwtDecoder()`:

```java
    static JwtDecoder sessaoJwtDecoder(RSAPublicKey publicKey, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(AUDIENCIA_SESSAO));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audiencia));
        return decoder;
    }
```

- [ ] **Step 5: Endurecer a validação do token de aprovação**

Em `AprovacaoTokenGatewayImpl`, adicione a constante e o import `org.springframework.security.oauth2.jwt.JwtClaimNames`:

```java
    private static final String EMISSOR = "back-end";
```

Em `gerarToken`, troque `.issuer("back-end")` por `.issuer(EMISSOR)`.

Em `validar`, troque o bloco `if (!SUBJECT.equals(jwt.getSubject())) { ... }` por:

```java
        boolean temAudiencia = jwt.getAudience() != null && !jwt.getAudience().isEmpty();
        if (!SUBJECT.equals(jwt.getSubject())
                || !EMISSOR.equals(jwt.getClaimAsString(JwtClaimNames.ISS))
                || temAudiencia) {
            throw new TokenAprovacaoInvalidoException("Link inválido ou expirado.");
        }
```

- [ ] **Step 6: Declarar o emissor nas propriedades**

Em `src/main/resources/application.properties`, logo abaixo de `jwt.private.key=...`:

```properties
jwt.issuer=${JWT_ISSUER:http://localhost:8080}
```

Em `src/test/resources/application.properties`, logo abaixo de `jwt.public.key=...`:

```properties
jwt.issuer=http://localhost:8080
```

(O arquivo de teste substitui o de `main` no classpath, então a propriedade precisa estar nos dois. O perfil `dev` herda o default de `main`.)

- [ ] **Step 7: Rodar e ver passar**

Run: `mvn test -Dtest='SessaoJwtDecoderUnitTest,AprovacaoTokenGatewayImplUnitTest,AprovacaoPublicaControllerIT'`
Expected: PASS em todos. O `AprovacaoPublicaControllerIT` prova que o link do e-mail continua funcionando.

- [ ] **Step 8: Suíte completa**

Run: `mvn test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/fiap/oficina/config/SecurityConfig.java \
        src/main/java/br/com/fiap/oficina/dataprovider/token/AprovacaoTokenGatewayImpl.java \
        src/main/resources/application.properties src/test/resources/application.properties \
        src/test/java/br/com/fiap/oficina/config/SessaoJwtDecoderUnitTest.java \
        src/test/java/br/com/fiap/oficina/dataprovider/token/AprovacaoTokenGatewayImplUnitTest.java
git commit -m "fix(security): require audience and issuer on session tokens"
```

---

### Task 4: Clientes bloqueados nas rotas de funcionário

**Files:**
- Modify: `src/main/java/br/com/fiap/oficina/config/SecurityConfig.java`
- Create: `src/test/java/br/com/fiap/oficina/controller/AutorizacaoPorPerfilIT.java`

**Interfaces:**
- Produces: regra `/api/minhas-ordens-servico/**` → `hasRole("CLIENTE")`; `/api/**` → `hasAnyRole("ADMIN", "RECEPCAO", "OPERADOR")`. `/api/auth/**` deixa de ser público.

- [ ] **Step 1: Escrever o teste**

```java
package br.com.fiap.oficina.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AutorizacaoPorPerfilIT {

    @Autowired MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/clientes",
            "/api/clientes/1",
            "/api/clientes/cpf-cnpj/52998224725",
            "/api/ordens-servico",
            "/api/ordens-servico/1",
            "/api/ordens-servico/numero/OS1",
            "/api/ordens-servico/metricas/tempo-medio",
            "/api/produtos",
            "/api/produtos/1",
            "/api/produtos/1/estoque/movimentacoes",
            "/api/produtos/estoque/movimentacoes",
            "/api/veiculos",
            "/api/veiculos/1",
            "/api/veiculos/placa/ABC1234",
            "/api/veiculos/cliente/1"
    })
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("Cliente autenticado não acessa rotas de funcionário")
    void clienteRecebe403EmRotaDeFuncionario(String rota) throws Exception {
        mockMvc.perform(get(rota)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Funcionário não acessa as rotas de minhas ordens de serviço")
    void funcionarioRecebe403EmMinhasOrdensServico() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Cadastro de funcionário exige autenticação")
    void registroSemTokenRecebe401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"novo@email.com\",\"password\":\"Senha@123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=AutorizacaoPorPerfilIT`
Expected: FAIL — as rotas devolvem 200/404 para `CLIENTE` em vez de 403, e o registro anônimo devolve 403 em vez de 401.

- [ ] **Step 3: Implementar as regras de URL**

Em `SecurityConfig.devFilterChain`, troque o bloco `authorizeHttpRequests` por:

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/h2-console/**", "/actuator/health", "/actuator/prometheus", "/aprovacao-os").permitAll()
                        .requestMatchers("/api/minhas-ordens-servico/**").hasRole("CLIENTE")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "RECEPCAO", "OPERADOR")
                        .anyRequest().authenticated())
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=AutorizacaoPorPerfilIT`
Expected: PASS nos 17 casos.

- [ ] **Step 5: Suíte completa**

Run: `mvn test`
Expected: `BUILD SUCCESS`. Os `*ControllerIT` existentes usam roles de funcionário e não devem mudar.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/fiap/oficina/config/SecurityConfig.java \
        src/test/java/br/com/fiap/oficina/controller/AutorizacaoPorPerfilIT.java
git commit -m "feat(security): restrict api routes by customer and staff roles"
```

---

### Task 5: `MinhasOrdensServicoController`

**Files:**
- Create: `src/main/java/br/com/fiap/oficina/entrypoint/controller/MinhasOrdensServicoController.java`
- Create: `src/test/java/br/com/fiap/oficina/controller/MinhasOrdensServicoControllerIT.java`

**Interfaces:**
- Consumes: `OrdemServicoUseCase.listarPorCliente(Long)` (existente), `OrdemServicoUseCase.buscarDoCliente(Long, Long)` (Task 2), `OrdemServicoDtoMapper.toResponse` (existente), regra `CLIENTE` (Task 4).
- Produces: `GET /api/minhas-ordens-servico` e `GET /api/minhas-ordens-servico/{id}`, `clienteId = Long.valueOf(jwt.getSubject())`.

- [ ] **Step 1: Escrever o teste de integração**

```java
package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MinhasOrdensServicoControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private Long clienteAna;
    private Long osAna;
    private Long osCarlos;

    @BeforeEach
    void criarDados() throws Exception {
        clienteAna = criar("/api/clientes", new ClienteRequest("Ana Lima", "71498053297",
                TipoDocumento.CPF, null, "ana@email.com", null));
        Long clienteCarlos = criar("/api/clientes", new ClienteRequest("Carlos Mendes", "52998224725",
                TipoDocumento.CPF, null, "carlos@email.com", null));
        Long veiculo = criar("/api/veiculos", new VeiculoRequest("XYZ9876", "Honda", "Civic", 2022, "Preto", null));

        osAna = criar("/api/ordens-servico", new OrdemServicoRequest(clienteAna, veiculo, "Motor falhando", null, null));
        osCarlos = criar("/api/ordens-servico", new OrdemServicoRequest(clienteCarlos, veiculo, "Freio rangendo", null, null));
    }

    private Long criar(String rota, Object corpo) throws Exception {
        String resposta = mockMvc.perform(post(rota)
                        .with(user("recepcao").roles("ADMIN", "RECEPCAO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    private RequestPostProcessor tokenDoCliente(Long clienteId) {
        return jwt().jwt(j -> j.subject(String.valueOf(clienteId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    @Test
    @DisplayName("Cliente lista apenas as próprias ordens de serviço")
    void clienteListaSomenteAsProprias() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico").with(tokenDoCliente(clienteAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(osAna));
    }

    @Test
    @DisplayName("Cliente consulta o detalhe de uma OS própria")
    void clienteConsultaOsPropria() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico/{id}", osAna).with(tokenDoCliente(clienteAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(osAna))
                .andExpect(jsonPath("$.status").value("RECEBIDA"));
    }

    @Test
    @DisplayName("OS de outro cliente responde 404, sem revelar que existe")
    void osDeOutroClienteResponde404() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico/{id}", osCarlos).with(tokenDoCliente(clienteAna)))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=MinhasOrdensServicoControllerIT`
Expected: FAIL — as chamadas devolvem 404 (rota não existe) onde o teste espera 200.

- [ ] **Step 3: Implementar o controller**

```java
package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.entrypoint.controller.mapper.OrdemServicoDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/minhas-ordens-servico")
@RequiredArgsConstructor
@Tag(name = "Minhas Ordens de Serviço", description = "Consulta das ordens de serviço do cliente autenticado")
public class MinhasOrdensServicoController {

    private final OrdemServicoUseCase ordemServicoUseCase;
    private final OrdemServicoDtoMapper mapper;

    @GetMapping
    @Operation(summary = "Listar as ordens de serviço do cliente autenticado")
    public ResponseEntity<List<OrdemServicoResponse>> listar(@AuthenticationPrincipal Jwt jwt) {
        List<OrdemServicoResponse> ordens = ordemServicoUseCase.listarPorCliente(clienteId(jwt)).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(ordens);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar uma ordem de serviço do cliente autenticado")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.buscarDoCliente(id, clienteId(jwt))));
    }

    private Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn test -Dtest=MinhasOrdensServicoControllerIT`
Expected: PASS nos 3 testes.

- [ ] **Step 5: Suíte completa + gate de cobertura**

Run: `mvn verify`
Expected: `BUILD SUCCESS`, incluindo `All coverage checks have been met`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/fiap/oficina/entrypoint/controller/MinhasOrdensServicoController.java \
        src/test/java/br/com/fiap/oficina/controller/MinhasOrdensServicoControllerIT.java
git commit -m "feat(os): add read-only service order routes for customers"
```

---

### Task 6: Remover o login da API

**Files:**
- Modify: `src/main/java/br/com/fiap/oficina/entrypoint/controller/AuthController.java`
- Modify: `src/main/java/br/com/fiap/oficina/core/usecase/AuthUseCase.java`
- Modify: `src/main/java/br/com/fiap/oficina/config/UseCaseConfig.java`
- Delete: `src/main/java/br/com/fiap/oficina/core/gateway/TokenAutenticacaoGateway.java`
- Delete: `src/main/java/br/com/fiap/oficina/dataprovider/token/TokenAutenticacaoGatewayImpl.java`
- Delete: `src/main/java/br/com/fiap/oficina/dto/request/LoginRequest.java`
- Delete: `src/main/java/br/com/fiap/oficina/dto/response/LoginResponse.java`
- Modify: `src/test/java/br/com/fiap/oficina/core/usecase/AuthUseCaseUnitTest.java`
- Modify: `src/test/java/br/com/fiap/oficina/controller/AuthControllerIT.java`

**Interfaces:**
- Produces: `AuthUseCase(UsuarioGateway, CriptografiaSenhaGateway)` com apenas `register(String, String)`. `POST /api/auth/register` permanece.

- [ ] **Step 1: Ajustar os testes primeiro**

Em `AuthUseCaseUnitTest`:
- remova os três testes `loginComCredenciaisValidas`, `loginUsuarioInexistente` e `loginSenhaIncorreta`;
- remova o campo `@Mock TokenAutenticacaoGateway tokenGateway;` e os imports `TokenAutenticacaoGateway`, `CredenciaisInvalidasException`, `anyList`, `anyLong`, `anyString`, `eq` e `java.util.Optional`;
- troque a construção no `setup()` por `useCase = new AuthUseCase(usuarioGateway, senhaGateway);`.

Em `AuthControllerIT`:
- remova os três testes `deveRetornar401LoginUsuarioInexistente`, `deveRetornar401LoginSenhaIncorreta` e `deveRetornar200ComTokenNoLoginComSucesso`;
- remova o import `br.com.fiap.oficina.dto.request.LoginRequest`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn test -Dtest=AuthUseCaseUnitTest`
Expected: FAIL de compilação — `constructor AuthUseCase ... cannot be applied to given types`.

- [ ] **Step 3: Enxugar `AuthUseCase`**

Substitua o arquivo inteiro por:

```java
package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Usuario;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;

import java.util.Locale;
import java.util.Set;

public class AuthUseCase {

    private static final String PERFIL_PADRAO = "OPERADOR";

    private final UsuarioGateway usuarioGateway;
    private final CriptografiaSenhaGateway senhaGateway;

    public AuthUseCase(UsuarioGateway usuarioGateway, CriptografiaSenhaGateway senhaGateway) {
        this.usuarioGateway = usuarioGateway;
        this.senhaGateway = senhaGateway;
    }

    public Usuario register(String username, String senha) {
        String normalizado = username.trim().toLowerCase(Locale.ROOT);
        if (usuarioGateway.existePorUsername(normalizado)) {
            throw new RegraDeNegocioException("Usuário já cadastrado");
        }
        Usuario novo = Usuario.builder()
                .username(normalizado)
                .password(senhaGateway.codificar(senha))
                .roles(Set.of(PERFIL_PADRAO))
                .build();
        return usuarioGateway.salvar(novo);
    }
}
```

- [ ] **Step 4: Remover a rota de login do controller**

Em `AuthController`, apague o método `login` inteiro e os imports `LoginRequest` e `LoginResponse`.

- [ ] **Step 5: Ajustar `UseCaseConfig`**

Troque o bean `authUseCase` por:

```java
    @Bean
    public AuthUseCase authUseCase(UsuarioGateway usuarioGateway, CriptografiaSenhaGateway criptografiaSenhaGateway) {
        return new AuthUseCase(usuarioGateway, criptografiaSenhaGateway);
    }
```

e remova o import `br.com.fiap.oficina.core.gateway.TokenAutenticacaoGateway`.

- [ ] **Step 6: Apagar as classes órfãs**

```bash
git rm src/main/java/br/com/fiap/oficina/core/gateway/TokenAutenticacaoGateway.java \
       src/main/java/br/com/fiap/oficina/dataprovider/token/TokenAutenticacaoGatewayImpl.java \
       src/main/java/br/com/fiap/oficina/dto/request/LoginRequest.java \
       src/main/java/br/com/fiap/oficina/dto/response/LoginResponse.java
```

- [ ] **Step 7: Confirmar que não sobrou referência**

Run: `grep -rn "TokenAutenticacao\|LoginRequest\|LoginResponse\|ResultadoLogin\|auth/login" src`
Expected: nenhuma saída.

- [ ] **Step 8: Suíte completa + cobertura**

Run: `mvn verify`
Expected: `BUILD SUCCESS`, com `All coverage checks have been met`.

- [ ] **Step 9: Commit**

```bash
git add -A src
git commit -m "refactor(auth): remove local login in favor of the auth lambda"
```

---

### Task 7: Manifestos, pipeline, seed e documentação

**Files:**
- Modify: `k8s/service.yaml`
- Modify: `k8s/configmap.yaml`
- Modify: `k8s/README.md`
- Modify: `.github/workflows/ci-cd.yml`
- Modify: `src/main/java/br/com/fiap/oficina/config/DataLoader.java`
- Modify: `postman/oficina-api.collection.json`
- Modify: `postman/oficina-api.environment.json`
- Modify: `README.md`
- Modify: `CLAUDE.md` (arquivo não rastreado pelo git: editar, mas **não** adicionar ao commit)

**Interfaces:**
- Consumes: propriedade `jwt.issuer`/`JWT_ISSUER` (Task 3); rotas da Lambda `POST /auth/funcionario` (`{username, senha}`), `POST /auth/cliente` (`{cpf, senha}`), `POST /auth/cliente/primeiro-acesso` (`{cpf, email, senha}`), todas devolvendo `{token, expiresIn}` (plano irmão).
- Produces: `Service` como NLB interno com tag `kubernetes.io/service-name = oficina/oficina-api`, que o Terraform do `lambda-auth` descobre por data source.

- [ ] **Step 1: `Service` como NLB interno**

Substitua `k8s/service.yaml` por:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: oficina-api
  namespace: oficina
  annotations:
    # NLB interno: a entrada publica e o API Gateway, que chega aqui por VPC Link
    # (Terraform em fiap-15soat-oficina-lambda-auth, que encontra este NLB pela tag
    # kubernetes.io/service-name). Trocar o tipo de load balancer exige recriar o Service.
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-internal: "true"
spec:
  type: LoadBalancer
  selector:
    app: oficina-api
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
```

- [ ] **Step 2: Emissor esperado no ConfigMap**

Em `k8s/configmap.yaml`, logo abaixo da linha `APP_PUBLIC_BASE_URL: "<APP_PUBLIC_BASE_URL>"`:

```yaml
  # Emissor esperado nos tokens de sessao: a URL do API Gateway, a mesma de APP_PUBLIC_BASE_URL.
  JWT_ISSUER: "<APP_PUBLIC_BASE_URL>"
```

- [ ] **Step 3: Conferir que o `sed` do workflow cobre as duas linhas**

Run:
```bash
APP_PUBLIC_BASE_URL=https://abc123.execute-api.us-east-1.amazonaws.com RDS_ENDPOINT=rds.local \
  sh -c 'sed "s|<RDS_ENDPOINT>|$RDS_ENDPOINT|;s|<APP_PUBLIC_BASE_URL>|$APP_PUBLIC_BASE_URL|" k8s/configmap.yaml' | grep -E "APP_PUBLIC_BASE_URL|JWT_ISSUER|DB_HOST"
```
Expected: as três linhas com os valores substituídos e nenhum `<...>` restante. (O `sed` sem `g` troca a primeira ocorrência **por linha**, e cada placeholder está numa linha própria.)

- [ ] **Step 4: Recriar o `Service` uma única vez no deploy**

Em `.github/workflows/ci-cd.yml`, no job `deploy`, insira este step **imediatamente antes** de `Renderizar e aplicar ConfigMap/Deployment/Service/HPA`:

```yaml
      # O controller nao converte um ELB classico em NLB: o Service antigo precisa ser
      # apagado uma vez. Depois da migracao a anotacao ja existe e o step nao faz nada.
      - name: Migrar Service para NLB interno (idempotente)
        run: |
          if kubectl get service oficina-api -n oficina >/dev/null 2>&1; then
            tipo=$(kubectl get service oficina-api -n oficina \
              -o jsonpath='{.metadata.annotations.service\.beta\.kubernetes\.io/aws-load-balancer-type}')
            if [ "$tipo" != "nlb" ]; then
              kubectl delete service oficina-api -n oficina
            fi
          fi
```

- [ ] **Step 5: Validar a sintaxe do workflow**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci-cd.yml')); print('ok')"`
Expected: `ok`.

- [ ] **Step 6: Corrigir o CPF inválido do cliente semeado**

O CPF `12345678901` do João tem dígitos verificadores inválidos, e a Lambda o rejeitaria com 400 na demonstração. Em `DataLoader.java`, troque `.cpfCnpj("12345678901")` por `.cpfCnpj("52998224725")`.

Run: `grep -rn "12345678901" --exclude-dir=target --exclude-dir=.git .`
Expected: sobra apenas a ocorrência na collection do Postman (tratada no Step 7) e, eventualmente, no `CHECKLIST.md`/docs, que não precisam mudar.

- [ ] **Step 7: Atualizar a collection e o environment do Postman**

Crie `/tmp/atualizar_postman.py` com o conteúdo abaixo e rode `python3 /tmp/atualizar_postman.py`:

```python
import json

COLECAO = "postman/oficina-api.collection.json"
AMBIENTE = "postman/oficina-api.environment.json"

SALVAR_TOKEN = [
    "if (pm.response.code === 200 || pm.response.code === 201) {",
    "    pm.environment.set('token', pm.response.json().token);",
    "}",
]


def requisicao(nome, rota, corpo, descricao):
    partes = [p for p in rota.split("/") if p]
    return {
        "name": nome,
        "event": [{"listen": "test", "script": {"exec": SALVAR_TOKEN, "type": "text/javascript"}}],
        "request": {
            "auth": {"type": "noauth"},
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "body": {"mode": "raw", "raw": json.dumps(corpo, ensure_ascii=False, indent=2)},
            "url": {"raw": "{{auth_url}}" + rota, "host": ["{{auth_url}}"], "path": partes},
            "description": descricao,
        },
        "response": [],
    }


with open(COLECAO, encoding="utf-8") as f:
    colecao = json.load(f)

auth = next(p for p in colecao["item"] if p["name"] == "Auth")
registrar = next(i for i in auth["item"] if i["name"] == "Registrar Novo Usuário")
auth["item"] = [
    requisicao("Login como ADMIN", "/auth/funcionario",
               {"username": "kaua@gmail.com", "senha": "Admin@123"},
               "Login de funcionário na Lambda. Pode criar/editar/deletar tudo."),
    requisicao("Login como RECEPCAO", "/auth/funcionario",
               {"username": "recepcao@oficina.com", "senha": "Recepcao@123"},
               "Login de funcionário na Lambda. Gerencia clientes, veículos e abertura de OS."),
    requisicao("Login como OPERADOR", "/auth/funcionario",
               {"username": "operador@oficina.com", "senha": "Operador@123"},
               "Login de funcionário na Lambda. Executa OS e movimenta estoque."),
    requisicao("Primeiro Acesso do Cliente", "/auth/cliente/primeiro-acesso",
               {"cpf": "52998224725", "email": "joao@email.com", "senha": "Cliente@123"},
               "Define a senha do cliente João (DataLoader) e já devolve o token. Só funciona uma vez."),
    requisicao("Login do Cliente (CPF)", "/auth/cliente",
               {"cpf": "52998224725", "senha": "Cliente@123"},
               "Login do cliente por CPF + senha. O token só acessa /api/minhas-ordens-servico."),
    registrar,
]

colecao["item"].insert(colecao["item"].index(auth) + 1, {
    "name": "Minhas Ordens de Serviço (Cliente)",
    "item": [
        {
            "name": "Listar Minhas OS",
            "request": {"method": "GET", "header": [],
                        "url": {"raw": "{{base_url}}/api/minhas-ordens-servico", "host": ["{{base_url}}"],
                                "path": ["api", "minhas-ordens-servico"]},
                        "description": "Exige token de Auth > Login do Cliente (CPF)."},
            "response": [],
        },
        {
            "name": "Detalhar Minha OS",
            "request": {"method": "GET", "header": [],
                        "url": {"raw": "{{base_url}}/api/minhas-ordens-servico/{{os_id}}", "host": ["{{base_url}}"],
                                "path": ["api", "minhas-ordens-servico", "{{os_id}}"]},
                        "description": "OS de outro cliente responde 404."},
            "response": [],
        },
    ],
})

texto = json.dumps(colecao, ensure_ascii=False, indent=2).replace("12345678901", "52998224725")
with open(COLECAO, "w", encoding="utf-8") as f:
    f.write(texto + "\n")

with open(AMBIENTE, encoding="utf-8") as f:
    ambiente = json.load(f)
if not any(v["key"] == "auth_url" for v in ambiente["values"]):
    ambiente["values"].insert(1, {
        "key": "auth_url", "value": "http://localhost:8080", "type": "default", "enabled": True,
        "description": "URL do API Gateway (output api_gateway_url do lambda-auth). Em deploy, igual a base_url.",
    })
for v in ambiente["values"]:
    if v["key"] == "token":
        v["description"] = "Preenchido automaticamente após executar um dos requests de Auth"
with open(AMBIENTE, "w", encoding="utf-8") as f:
    f.write(json.dumps(ambiente, ensure_ascii=False, indent=2) + "\n")
```

Run: `python3 -c "import json;c=json.load(open('postman/oficina-api.collection.json'));print([i['name'] for i in c['item'][0]['item']]);print(c['item'][1]['name'])"`
Expected: `['Login como ADMIN', 'Login como RECEPCAO', 'Login como OPERADOR', 'Primeiro Acesso do Cliente', 'Login do Cliente (CPF)', 'Registrar Novo Usuário']` e `Minhas Ordens de Serviço (Cliente)`.

- [ ] **Step 8: README — seção de segurança**

Em `README.md`, substitua:

```markdown
Todos os endpoints exigem **JWT Bearer token**, exceto:
- `POST /api/auth/login`
```

por:

```markdown
A API **não emite tokens de sessão**: o login acontece na Lambda `oficina-auth`
(repositório [`fiap-15soat-oficina-lambda-auth`](https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-lambda-auth)),
exposta pelo mesmo API Gateway que encaminha as chamadas para esta API. O Gateway valida o JWT
(RS256, `aud = oficina-api`, `iss` = URL do Gateway) e a API valida de novo.

| Quem | Como autentica | Acessa |
|---|---|---|
| Funcionário | `POST /auth/funcionario` com e-mail + senha | `/api/**` conforme a role (`ADMIN`, `RECEPCAO`, `OPERADOR`) |
| Cliente | `POST /auth/cliente` com CPF + senha (senha criada em `POST /auth/cliente/primeiro-acesso`) | somente `GET /api/minhas-ordens-servico` e `GET /api/minhas-ordens-servico/{id}` |

Todos os endpoints exigem **JWT Bearer token**, exceto:
```

- [ ] **Step 9: README — tabela de endpoints de autenticação**

Substitua o bloco que começa em `### Autenticação — \`/api/auth\`` e termina no fechamento do exemplo de login (a linha ```` ``` ```` depois de `{ "token": "<JWT>", "expiresIn": 28800 }`) por:

````markdown
### Autenticação

O login fica na Lambda, atrás do API Gateway (`<gateway>` = output `api_gateway_url` do `lambda-auth`).

| Método | Endpoint | Autenticação | Descrição |
|--------|----------|-------------|-----------|
| POST | `<gateway>/auth/funcionario` | Pública | Login de funcionário, devolve JWT de 8 h |
| POST | `<gateway>/auth/cliente` | Pública | Login de cliente por CPF + senha, devolve JWT de 1 h |
| POST | `<gateway>/auth/cliente/primeiro-acesso` | Pública | Cliente ativo define a senha (CPF + e-mail do cadastro) |
| POST | `/api/auth/register` | ADMIN | Cadastra novo usuário (role OPERADOR) |

**Login de funcionário — exemplo:**
```json
// Request
{ "username": "kaua@gmail.com", "senha": "Admin@123" }

// Response
{ "token": "<JWT>", "expiresIn": 28800 }
```

### Minhas ordens de serviço — `/api/minhas-ordens-servico` (role `CLIENTE`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/minhas-ordens-servico` | Lista as OS do cliente do token |
| GET | `/api/minhas-ordens-servico/{id}` | Detalha uma OS do cliente do token (OS de outro cliente → 404) |

**Rodando localmente**, sem Gateway: gere um token com o script do `lambda-auth`
(`npm run token:local -- --sub kaua@gmail.com --roles ADMIN`), que assina com a mesma
`jwt.private.key` e `iss = http://localhost:8080`.
````

- [ ] **Step 10: README — secret `APP_PUBLIC_BASE_URL`**

Substitua a linha da tabela de secrets que começa com `| \`APP_PUBLIC_BASE_URL\`` por:

```markdown
| `APP_PUBLIC_BASE_URL` | URL do API Gateway — usada nos links do e-mail de aprovação **e** como emissor esperado dos tokens (`JWT_ISSUER`) | Output `api_gateway_url` do repositório `fiap-15soat-oficina-lambda-auth` |
```

- [ ] **Step 11: `k8s/README.md`**

Substitua a linha:

```
kubectl get svc oficina-api -n oficina        # EXTERNAL-IP do LoadBalancer
```

por:

```
kubectl get svc oficina-api -n oficina        # EXTERNAL-IP do NLB interno (so resolve dentro da VPC; a entrada publica e o API Gateway)
```

- [ ] **Step 12: `CLAUDE.md` (não commitar)**

Em `CLAUDE.md`, seção "Segurança e autorização", substitua os dois primeiros bullets por:

```markdown
- Autenticação: **a API não emite tokens de sessão.** O login (funcionário por e-mail + senha, cliente por CPF + senha) é da Lambda em `fiap-15soat-oficina-lambda-auth`, atrás do API Gateway. A API só valida: RS256 com o par RSA acima, `aud = oficina-api` e `iss = jwt.issuer` (`JWT_ISSUER`, a URL do Gateway), em `SecurityConfig.sessaoJwtDecoder`. Roles vêm da claim `roles`, com prefixo `ROLE_`.
- Autorização em duas camadas: **a URL separa cliente de funcionário** (`/api/minhas-ordens-servico/**` → `CLIENTE`; resto de `/api/**` → `ADMIN`/`RECEPCAO`/`OPERADOR`, no `SecurityConfig`), e **`@PreAuthorize` separa perfis de funcionário** entre si. Rota nova de funcionário já nasce fechada para clientes; rota de cliente precisa ficar sob `/api/minhas-ordens-servico/` e tirar o `clienteId` do `sub` do token, nunca de parâmetro.
- `cliente.senha_hash` existe só na entidade JPA (ignorada nos mappers); quem lê e escreve é a Lambda.
```

No bullet "Gateways de saída não-JPA", troque `` `TokenAutenticacaoGateway`/`TokenAprovacaoGateway` (JWT) `` por `` `TokenAprovacaoGateway` (JWT do link de aprovação) ``.

- [ ] **Step 13: Suíte completa**

Run: `mvn verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 14: Commit**

```bash
git add k8s/service.yaml k8s/configmap.yaml k8s/README.md .github/workflows/ci-cd.yml \
        src/main/java/br/com/fiap/oficina/config/DataLoader.java \
        postman/oficina-api.collection.json postman/oficina-api.environment.json README.md
git commit -m "docs(auth): document gateway login and expose api through internal nlb"
```

---

### Task 8: Fechamento

- [ ] **Step 1: Verificação final**

Run: `mvn verify`
Expected: `BUILD SUCCESS` e `All coverage checks have been met`. Anote o total de testes do relatório do surefire.

- [ ] **Step 2: Conferir o histórico da branch**

Run: `git log --oneline master..HEAD`
Expected: 7 commits, um por tarefa (Tasks 1 a 7), todos com assunto em inglês e sem corpo.

- [ ] **Step 3: Não fazer merge nem deploy ainda**

O deploy desta branch **quebra o login em produção** até a Lambda existir. A ordem de subida está no plano irmão (`2026-09-15-lambda-auth.md`, Task 9). Abrir o PR só com aprovação do usuário.
