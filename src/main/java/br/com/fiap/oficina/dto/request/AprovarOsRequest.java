package br.com.fiap.oficina.dto.request;

import jakarta.validation.constraints.NotNull;

public record AprovarOsRequest(
        @NotNull(message = "Decisão de aprovação é obrigatória")
        Boolean aprovado
) {}
