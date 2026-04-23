package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.domain.enums.TipoProduto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProdutoResponse(
    Long id,
    String nome,
    String descricao,
    TipoProduto tipo,
    BigDecimal precoUnitario,
    String unidadeMedida,
    Boolean ativo,
    Integer saldoEstoque,
    LocalDateTime criadoEm
) {}
