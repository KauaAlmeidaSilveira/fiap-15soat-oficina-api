package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OsItem {

    private Long id;
    private Long produtoId;
    private String produtoNome;
    private TipoProduto produtoTipo;
    private Integer quantidade;
    private BigDecimal precoUnitario;
    private String observacao;

    public BigDecimal subtotal() {
        if (quantidade == null || precoUnitario == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(quantidade).multiply(precoUnitario);
    }
}
