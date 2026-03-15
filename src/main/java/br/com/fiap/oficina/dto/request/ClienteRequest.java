package br.com.fiap.oficina.dto.request;

import br.com.fiap.oficina.domain.enums.TipoDocumento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100)
    String nome,

    @NotBlank(message = "CPF/CNPJ é obrigatório")
    @Pattern(regexp = "^(\\d{11}|\\d{14})$", message = "CPF deve ter 11 dígitos ou CNPJ 14 dígitos (somente números)")
    String cpfCnpj,

    @NotNull(message = "Tipo de documento é obrigatório")
    TipoDocumento tipoDocumento,

    @Pattern(regexp = "^\\d{10,11}$", message = "Telefone deve ter 10 ou 11 dígitos")
    String telefone,

    @Email(message = "E-mail inválido")
    @Size(max = 150)
    String email,

    @Size(max = 255)
    String endereco
) {}
