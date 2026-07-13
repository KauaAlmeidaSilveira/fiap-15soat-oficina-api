package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;

import java.time.LocalDateTime;

public record MovimentacaoEstoqueResponse(
    Long id,
    Long produtoId,
    String produtoNome,
    TipoMovimentacao tipo,
    Integer quantidade,
    String motivo,
    Long ordemServicoId,
    LocalDateTime criadoEm
) {}
