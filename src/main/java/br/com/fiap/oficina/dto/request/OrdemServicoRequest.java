package br.com.fiap.oficina.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record OrdemServicoRequest(

    @NotNull(message = "Cliente é obrigatório")
    Long clienteId,

    @NotNull(message = "Veículo é obrigatório")
    Long veiculoId,

    @Size(max = 500)
    String descricaoProblema,

    @Size(max = 500)
    String observacoes,

    @Valid
    List<OsItemRequest> itens
) {
    public record OsItemRequest(

        @NotNull(message = "Produto é obrigatório")
        Long produtoId,

        @NotNull(message = "Quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
        BigDecimal quantidade,

        BigDecimal precoUnitario,

        @Size(max = 255)
        String observacao
    ) {}
}
