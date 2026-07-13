package br.com.fiap.oficina.dataprovider.token;

import br.com.fiap.oficina.core.domain.exception.TokenAprovacaoInvalidoException;
import br.com.fiap.oficina.core.gateway.TokenAprovacaoGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AprovacaoTokenGatewayImpl implements TokenAprovacaoGateway {

    private static final String SUBJECT = "os-aprovacao";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private Clock clock = Clock.systemUTC();

    @Value("${app.aprovacao-email.token-validade-dias:7}")
    private long validadeDias;

    @Override
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

    @Override
    public TokenAprovacao validar(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new TokenAprovacaoInvalidoException("Link inválido ou expirado.");
        }
        if (!SUBJECT.equals(jwt.getSubject())) {
            throw new TokenAprovacaoInvalidoException("Link inválido ou expirado.");
        }
        Long osId = Long.valueOf(jwt.getClaimAsString("osId"));
        Boolean aprovado = jwt.getClaim("aprovado");
        return new TokenAprovacao(osId, Boolean.TRUE.equals(aprovado));
    }
}
