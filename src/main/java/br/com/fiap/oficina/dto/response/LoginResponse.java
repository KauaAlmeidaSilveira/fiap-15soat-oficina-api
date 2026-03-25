package br.com.fiap.oficina.dto.response;

public record LoginResponse(String token, Long expiresIn) {
}
