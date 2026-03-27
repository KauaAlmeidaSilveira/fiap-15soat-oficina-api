package br.com.fiap.oficina.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Usuário é obrigatório")
        @Email(message = "Usuário deve ser um e-mail válido")
        @Size(max = 150, message = "Usuário deve ter no máximo 150 caracteres")
        String username,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Senha deve conter ao menos uma letra maiúscula, um número e um caractere especial"
        )
        String password
) {}
