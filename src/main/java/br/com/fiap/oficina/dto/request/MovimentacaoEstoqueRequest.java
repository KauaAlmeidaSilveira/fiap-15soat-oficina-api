package br.com.fiap.oficina.dto.request;

import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovimentacaoEstoqueRequest(

    @NotNull(message = "Produto é obrigatório")
    Long produtoId,

    // Tipo inferido pelo endpoint (/estoque/entrada ou /estoque/saida). Campo ignorado nas novas rotas.
    TipoMovimentacao tipo,

    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "Quantidade deve ser maior que zero")
    Integer quantidade,

    @Size(max = 255)
    String motivo,

    Long ordemServicoId
) {}
