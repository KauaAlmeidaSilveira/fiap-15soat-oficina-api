package br.com.fiap.oficina.dto.request;

import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MovimentacaoEstoqueRequest(

    @NotNull(message = "Produto é obrigatório")
    Long produtoId,

    @NotNull(message = "Tipo de movimentação é obrigatório")
    TipoMovimentacao tipo,

    @NotNull(message = "Quantidade é obrigatória")
    @DecimalMin(value = "0.001", message = "Quantidade deve ser maior que zero")
    BigDecimal quantidade,

    @Size(max = 255)
    String motivo,

    Long ordemServicoId
) {}
