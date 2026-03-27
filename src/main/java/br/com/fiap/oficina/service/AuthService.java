package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.model.Role;
import br.com.fiap.oficina.domain.model.User;
import br.com.fiap.oficina.domain.repository.RoleRepository;
import br.com.fiap.oficina.domain.repository.UserRepository;
import br.com.fiap.oficina.dto.request.LoginRequest;
import br.com.fiap.oficina.dto.request.RegisterRequest;
import br.com.fiap.oficina.dto.response.LoginResponse;
import br.com.fiap.oficina.dto.response.RegisterResponse;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@AllArgsConstructor
public class AuthService {

    private final JwtEncoder jwtEncoder;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse getToken(LoginRequest loginRequest) {
        String normalizedUsername = loginRequest.username().trim().toLowerCase(Locale.ROOT);

        var user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        if (!userService.isPasswordValid(loginRequest, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
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

        return new LoginResponse(jwtValue, expiresIn);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        String normalizedUsername = registerRequest.username().trim().toLowerCase(Locale.ROOT);

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new RegraDeNegocioException("Usuário já cadastrado");
        }

        Role basicRole = roleRepository.findById(Role.Values.BASIC.getRoleId())
                .orElseThrow(() -> new IllegalStateException("Perfil padrão BASIC não encontrado"));

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(registerRequest.password()));
        user.setRoles(Set.of(basicRole));

        User savedUser = userRepository.save(user);

        return new RegisterResponse(savedUser.getId(), savedUser.getUsername());
    }

}
