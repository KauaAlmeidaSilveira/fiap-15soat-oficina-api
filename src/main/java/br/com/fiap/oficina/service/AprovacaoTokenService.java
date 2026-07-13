package br.com.fiap.oficina.service;

import br.com.fiap.oficina.core.domain.exception.TokenAprovacaoInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AprovacaoTokenService {

    private static final String SUBJECT = "os-aprovacao";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private Clock clock = Clock.systemUTC();

    @Value("${app.aprovacao-email.token-validade-dias:7}")
    private long validadeDias;

    public String gerarToken(Long osId, boolean aprovado) {
        Instant agora = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("back-end")
                .subject(SUBJECT)
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(validadeDias * 24 * 60 * 60))
                .claim("osId", String.valueOf(osId))
                .claim("aprovado", aprovado)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public TokenAprovacaoDecodificado validar(String token) {
        Jwt jwt;
        try {
            // NimbusJwtDecoder já valida a assinatura e a expiração (exp) internamente,
            // lançando JwtException para token adulterado ou expirado.
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new TokenAprovacaoInvalidoException("Link inválido ou expirado.");
        }

        if (!SUBJECT.equals(jwt.getSubject())) {
            throw new TokenAprovacaoInvalidoException("Link inválido ou expirado.");
        }

        Long osId = Long.valueOf(jwt.getClaimAsString("osId"));
        Boolean aprovado = jwt.getClaim("aprovado");
        return new TokenAprovacaoDecodificado(osId, Boolean.TRUE.equals(aprovado));
    }

    public record TokenAprovacaoDecodificado(Long osId, boolean aprovado) {}
}
