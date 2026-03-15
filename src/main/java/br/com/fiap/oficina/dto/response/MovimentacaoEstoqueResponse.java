package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.domain.enums.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoEstoqueResponse(
    Long id,
    Long produtoId,
    String produtoNome,
    TipoMovimentacao tipo,
    BigDecimal quantidade,
    String motivo,
    Long ordemServicoId,
    LocalDateTime criadoEm
) {}
