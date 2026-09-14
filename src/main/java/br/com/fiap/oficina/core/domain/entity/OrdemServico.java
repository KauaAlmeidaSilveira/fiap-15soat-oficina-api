package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdemServico {

    private Long id;
    private String numero;
    private Cliente cliente;
    private Veiculo veiculo;

    @Builder.Default
    private StatusOS status = StatusOS.RECEBIDA;

    private String descricaoProblema;
    private String observacoes;

    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private LocalDateTime dataInicio;
    private LocalDateTime dataFim;
    private LocalDateTime dataEntrega;
    private LocalDateTime dataAprovacao;
    private LocalDateTime statusAlteradoEm;
    private LocalDateTime criadoEm;

    @Builder.Default
    private List<OsItem> itens = new ArrayList<>();

    public void avancarStatus() {
        StatusOS anterior = status;
        status = switch (status) {
            case RECEBIDA -> StatusOS.EM_DIAGNOSTICO;
            case EM_DIAGNOSTICO -> StatusOS.AGUARDANDO_APROVACAO;
            case AGUARDANDO_APROVACAO -> StatusOS.EM_EXECUCAO;
            case EM_EXECUCAO -> StatusOS.FINALIZADA;
            case FINALIZADA -> StatusOS.ENTREGUE;
            case ENTREGUE -> throw new RegraDeNegocioException("OS já foi entregue ao cliente.");
            case REPROVADA -> throw new RegraDeNegocioException("OS reprovada não pode ter o status avançado.");
        };
        switch (status) {
            case EM_EXECUCAO -> dataInicio = LocalDateTime.now();
            case FINALIZADA -> dataFim = LocalDateTime.now();
            case ENTREGUE -> dataEntrega = LocalDateTime.now();
            default -> { }
        }
        if (anterior == StatusOS.AGUARDANDO_APROVACAO) {
            dataAprovacao = LocalDateTime.now();
        }
        statusAlteradoEm = LocalDateTime.now();
    }

    public void aprovar(boolean aprovado) {
        if (status != StatusOS.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException(
                    "A OS precisa estar com status AGUARDANDO_APROVACAO para ser aprovada ou reprovada.");
        }
        if (aprovado) {
            dataAprovacao = LocalDateTime.now();
            status = StatusOS.EM_EXECUCAO;
            dataInicio = LocalDateTime.now();
        } else {
            status = StatusOS.REPROVADA;
        }
        statusAlteradoEm = LocalDateTime.now();
    }

    public void adicionarItem(OsItem item) {
        if (status == StatusOS.FINALIZADA || status == StatusOS.ENTREGUE || status == StatusOS.REPROVADA) {
            throw new RegraDeNegocioException(
                    "Não é possível adicionar itens a uma OS finalizada, entregue ou reprovada.");
        }
        itens.add(item);
        recalcularTotal();
    }

    public void removerItem(OsItem item) {
        itens.remove(item);
        recalcularTotal();
    }

    public void recalcularTotal() {
        valorTotal = itens.stream().map(OsItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Duration tempoNoStatusAtual() {
        LocalDateTime desde = statusAlteradoEm != null ? statusAlteradoEm : criadoEm;
        return desde != null ? Duration.between(desde, LocalDateTime.now()) : Duration.ZERO;
    }

    public boolean isAprovada() {
        return dataAprovacao != null;
    }
}
