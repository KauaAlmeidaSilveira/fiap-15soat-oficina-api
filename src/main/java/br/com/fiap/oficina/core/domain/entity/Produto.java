package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    private Long id;
    private String nome;
    private String descricao;
    private TipoProduto tipo;
    private BigDecimal precoUnitario;
    private String unidadeMedida;
    private Boolean ativo;
    private Integer saldoEstoque;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
