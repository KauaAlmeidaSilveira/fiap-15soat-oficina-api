package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.EstoqueGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;

import java.util.List;

public class ProdutoUseCase {

    private final ProdutoGateway produtoGateway;
    private final EstoqueGateway estoqueGateway;

    public ProdutoUseCase(ProdutoGateway produtoGateway, EstoqueGateway estoqueGateway) {
        this.produtoGateway = produtoGateway;
        this.estoqueGateway = estoqueGateway;
    }

    public Produto criar(Produto novo, Integer quantidadeInicial) {
        novo.setAtivo(true);
        Produto salvo = produtoGateway.salvar(novo);

        if (TipoProduto.PECA.equals(salvo.getTipo())) {
            estoqueGateway.criarSaldoInicial(salvo.getId());
            if (quantidadeInicial != null && quantidadeInicial > 0) {
                estoqueGateway.registrarMovimentacao(salvo.getId(), TipoMovimentacao.ENTRADA,
                        quantidadeInicial, "Estoque inicial - cadastro do produto", null);
                estoqueGateway.sincronizarSaldo(salvo.getId());
            }
        }
        return buscarPorId(salvo.getId());
    }

    public Produto buscarPorId(Long id) {
        return produtoGateway.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
    }

    public List<Produto> listarTodos() {
        return produtoGateway.listarTodos();
    }

    public List<Produto> listarPorTipo(TipoProduto tipo) {
        return produtoGateway.listarPorTipo(tipo);
    }

    public Produto atualizar(Long id, Produto dados) {
        Produto existente = buscarPorId(id);
        existente.setNome(dados.getNome());
        existente.setDescricao(dados.getDescricao());
        existente.setPrecoUnitario(dados.getPrecoUnitario());
        existente.setUnidadeMedida(dados.getUnidadeMedida());
        return produtoGateway.salvar(existente);
    }

    public void inativar(Long id) {
        Produto existente = buscarPorId(id);
        existente.setAtivo(false);
        produtoGateway.salvar(existente);
    }

    public MovimentacaoEstoque registrarMovimentacao(Long produtoId, TipoMovimentacao tipo,
                                                     Integer quantidade, String motivo, Long ordemServicoId) {
        Produto produto = buscarPorId(produtoId);
        if (!TipoProduto.PECA.equals(produto.getTipo())) {
            throw new RegraDeNegocioException("Controle de estoque é aplicável apenas a peças.");
        }
        if (tipo == TipoMovimentacao.SAIDA) {
            int saldoAtual = estoqueGateway.obterSaldoAtual(produtoId);
            if (saldoAtual < quantidade) {
                throw new IllegalStateException(
                        "Saldo insuficiente. Disponível: " + saldoAtual + ", solicitado: " + quantidade);
            }
        }
        MovimentacaoEstoque mov = estoqueGateway.registrarMovimentacao(produtoId, tipo, quantidade, motivo, ordemServicoId);
        estoqueGateway.sincronizarSaldo(produtoId);
        return mov;
    }

    public List<MovimentacaoEstoque> listarMovimentacoes(Long produtoId) {
        buscarPorId(produtoId);
        return estoqueGateway.listarMovimentacoesPorProduto(produtoId);
    }

    public List<MovimentacaoEstoque> listarTodasMovimentacoes() {
        return estoqueGateway.listarTodasMovimentacoes();
    }
}
