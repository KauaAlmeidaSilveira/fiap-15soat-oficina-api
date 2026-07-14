package br.com.fiap.oficina.dataprovider.token;

import br.com.fiap.oficina.core.domain.exception.TokenAprovacaoInvalidoException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AprovacaoTokenGatewayImplUnitTest {

    private AprovacaoTokenGatewayImpl gateway;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        JwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        JWK jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));

        gateway = new AprovacaoTokenGatewayImpl(jwtEncoder, jwtDecoder);
        ReflectionTestUtils.setField(gateway, "validadeDias", 7L);
    }

    @Test
    @DisplayName("Deve gerar e validar token de aprovação corretamente")
    void deveGerarEValidarTokenAprovado() {
        String token = gateway.gerarToken(42L, true);

        var decodificado = gateway.validar(token);

        assertThat(decodificado.osId()).isEqualTo(42L);
        assertThat(decodificado.aprovado()).isTrue();
    }

    @Test
    @DisplayName("Deve gerar e validar token de recusa corretamente")
    void deveGerarEValidarTokenRecusado() {
        String token = gateway.gerarToken(7L, false);

        var decodificado = gateway.validar(token);

        assertThat(decodificado.osId()).isEqualTo(7L);
        assertThat(decodificado.aprovado()).isFalse();
    }

    @Test
    @DisplayName("Deve lançar exceção para token adulterado/assinatura inválida")
    void deveLancarExcecaoParaTokenInvalido() {
        assertThatThrownBy(() -> gateway.validar("token.invalido.aqui"))
                .isInstanceOf(TokenAprovacaoInvalidoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção para token expirado")
    void deveLancarExcecaoParaTokenExpirado() {
        Instant passado = Instant.now().minus(10, ChronoUnit.DAYS);
        ReflectionTestUtils.setField(gateway, "clock", Clock.fixed(passado, ZoneOffset.UTC));
        String token = gateway.gerarToken(1L, true);

        ReflectionTestUtils.setField(gateway, "clock", Clock.systemUTC());

        assertThatThrownBy(() -> gateway.validar(token))
                .isInstanceOf(TokenAprovacaoInvalidoException.class)
                .hasMessageContaining("expirado");
    }
}
