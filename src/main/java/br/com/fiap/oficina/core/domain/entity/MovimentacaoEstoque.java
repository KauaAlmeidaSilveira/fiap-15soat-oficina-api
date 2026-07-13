package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoEstoque {

    private Long id;
    private Long produtoId;
    private String produtoNome;
    private TipoMovimentacao tipo;
    private Integer quantidade;
    private String motivo;
    private Long ordemServicoId;
    private LocalDateTime criadoEm;
}
