package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;

import java.util.List;

public interface EstoqueGateway {

    void criarSaldoInicial(Long produtoId);

    int obterSaldoAtual(Long produtoId);

    MovimentacaoEstoque registrarMovimentacao(Long produtoId, TipoMovimentacao tipo,
                                              Integer quantidade, String motivo, Long ordemServicoId);

    void sincronizarSaldo(Long produtoId);

    List<MovimentacaoEstoque> listarMovimentacoesPorProduto(Long produtoId);

    List<MovimentacaoEstoque> listarTodasMovimentacoes();
}
