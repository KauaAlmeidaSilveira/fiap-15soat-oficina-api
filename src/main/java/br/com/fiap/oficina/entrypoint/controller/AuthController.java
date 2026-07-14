package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.usecase.AuthUseCase;
import br.com.fiap.oficina.dto.request.LoginRequest;
import br.com.fiap.oficina.dto.request.RegisterRequest;
import br.com.fiap.oficina.dto.response.LoginResponse;
import br.com.fiap.oficina.dto.response.RegisterResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        var resultado = authUseCase.login(loginRequest.username(), loginRequest.password());
        return ResponseEntity.ok(new LoginResponse(resultado.token(), resultado.expiresIn()));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        var usuario = authUseCase.register(registerRequest.username(), registerRequest.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(usuario.getId(), usuario.getUsername()));
    }
}
