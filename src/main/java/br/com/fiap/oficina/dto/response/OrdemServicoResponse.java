package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.domain.enums.StatusOS;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrdemServicoResponse(
    Long id,
    String numero,
    ClienteResponse cliente,
    VeiculoResponse veiculo,
    StatusOS status,
    String descricaoProblema,
    String observacoes,
    BigDecimal valorTotal,
    LocalDateTime dataInicio,
    LocalDateTime dataFim,
    LocalDateTime dataEntrega,
    LocalDateTime dataAprovacao,
    LocalDateTime criadoEm,
    List<OsItemResponse> itens
) {
    public record OsItemResponse(
        Long id,
        Long produtoId,
        String produtoNome,
        String produtoTipo,
        BigDecimal quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal,
        String observacao
    ) {}
}
