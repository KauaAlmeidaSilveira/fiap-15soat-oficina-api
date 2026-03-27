package br.com.fiap.oficina.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

	@NotBlank(message = "Usuário é obrigatório")
	String username,

	@NotBlank(message = "Senha é obrigatória")
	String password
) {}
