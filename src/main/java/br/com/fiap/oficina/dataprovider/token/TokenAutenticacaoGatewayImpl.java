package br.com.fiap.oficina.dataprovider.token;

import br.com.fiap.oficina.core.gateway.TokenAutenticacaoGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenAutenticacaoGatewayImpl implements TokenAutenticacaoGateway {

    private final JwtEncoder jwtEncoder;

    @Override
    public String gerarToken(String username, List<String> roles, long expiracaoSegundos) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("back-end")
                .issuedAt(now)
                .subject(username)
                .expiresAt(now.plusSeconds(expiracaoSegundos))
                .claim("roles", roles)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
