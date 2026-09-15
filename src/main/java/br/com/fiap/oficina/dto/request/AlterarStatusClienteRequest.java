package br.com.fiap.oficina.dto.request;

import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import jakarta.validation.constraints.NotNull;

public record AlterarStatusClienteRequest(

    @NotNull(message = "Status é obrigatório")
    StatusCliente status
) {}
