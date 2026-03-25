package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.model.User;
import br.com.fiap.oficina.dto.request.LoginRequest;
import br.com.fiap.oficina.dto.response.LoginResponse;
import br.com.fiap.oficina.service.UserService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final JwtEncoder jwtEncoder;

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody LoginRequest loginRequest) {
        User user = userService.findByUsername(loginRequest.username());

        if (!userService.isPasswordValid(loginRequest, user.getPassword())) {
            return ResponseEntity.status(401).body("DEU RUIM");
        }

        Instant now = Instant.now();
        long expiresIn = 300L;

        var claims = JwtClaimsSet.builder()
                .issuer("back-end")
                .issuedAt(now)
                .subject(user.getUsername())
                .expiresAt(now.plusSeconds(expiresIn))
                .build();

        var jwtValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return ResponseEntity.ok(new LoginResponse(jwtValue, expiresIn));
    }

}
