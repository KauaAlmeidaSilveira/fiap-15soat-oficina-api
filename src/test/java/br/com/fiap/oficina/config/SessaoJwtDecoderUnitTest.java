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
