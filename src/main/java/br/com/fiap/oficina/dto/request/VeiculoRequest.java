package br.com.fiap.oficina.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VeiculoRequest(

    @NotBlank(message = "Placa é obrigatória")
    @Pattern(regexp = "^[A-Z]{3}\\d{4}$|^[A-Z]{3}\\d[A-Z]\\d{2}$",
             message = "Placa inválida. Use formato antigo (ABC1234) ou Mercosul (ABC1D23)")
    String placa,

    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 50)
    String marca,

    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 80)
    String modelo,

    @NotNull(message = "Ano é obrigatório")
    Integer ano,

    @Size(max = 20)
    String cor,

    @Size(max = 17)
    String chassi
) {}
