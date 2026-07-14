package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.EstoqueGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoUseCaseUnitTest {

    @Mock ProdutoGateway produtoGateway;
    @Mock EstoqueGateway estoqueGateway;
    ProdutoUseCase produtoUseCase;

    private Produto peca;
    private Produto servico;

    @BeforeEach
    void setup() {
        produtoUseCase = new ProdutoUseCase(produtoGateway, estoqueGateway);
        peca = Produto.builder().id(1L).nome("Filtro de óleo").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("50.00")).ativo(true).saldoEstoque(10).build();
        servico = Produto.builder().id(2L).nome("Troca de óleo").tipo(TipoProduto.SERVICO)
                .precoUnitario(new BigDecimal("80.00")).ativo(true).build();
    }

    @Test
    @DisplayName("Deve criar peça e inicializar saldo de estoque")
    void deveCriarPecaComSaldoEstoque() {
        when(produtoGateway.salvar(any(Produto.class))).thenReturn(peca);
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));

        Produto criado = produtoUseCase.criar(
                Produto.builder().nome("Filtro de óleo").tipo(TipoProduto.PECA)
                        .precoUnitario(new BigDecimal("50.00")).build(),
                10);

        assertThat(criado).isNotNull();
        verify(estoqueGateway).criarSaldoInicial(1L);
        verify(estoqueGateway).registrarMovimentacao(eq(1L), eq(TipoMovimentacao.ENTRADA), eq(10), any(), isNull());
        verify(estoqueGateway).sincronizarSaldo(1L);
    }

    @Test
    @DisplayName("Deve criar serviço SEM inicializar saldo de estoque")
    void deveCriarServicoSemSaldoEstoque() {
        when(produtoGateway.salvar(any(Produto.class))).thenReturn(servico);
        when(produtoGateway.buscarPorId(2L)).thenReturn(Optional.of(servico));

        produtoUseCase.criar(
                Produto.builder().nome("Troca de óleo").tipo(TipoProduto.SERVICO)
                        .precoUnitario(new BigDecimal("80.00")).build(),
                null);

        verify(estoqueGateway, never()).criarSaldoInicial(any());
        verify(estoqueGateway, never()).registrarMovimentacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve registrar entrada de estoque corretamente")
    void deveRegistrarEntradaEstoque() {
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        when(estoqueGateway.registrarMovimentacao(eq(1L), eq(TipoMovimentacao.ENTRADA), eq(5), any(), isNull()))
                .thenReturn(MovimentacaoEstoque.builder().id(1L).produtoId(1L).tipo(TipoMovimentacao.ENTRADA).quantidade(5).build());

        MovimentacaoEstoque mov = produtoUseCase.registrarMovimentacao(1L, TipoMovimentacao.ENTRADA, 5, "compra", null);

        assertThat(mov.getQuantidade()).isEqualTo(5);
        verify(estoqueGateway).sincronizarSaldo(1L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao movimentar estoque de serviço")
    void deveLancarExcecaoMovimentacaoServico() {
        when(produtoGateway.buscarPorId(2L)).thenReturn(Optional.of(servico));

        assertThatThrownBy(() -> produtoUseCase.registrarMovimentacao(2L, TipoMovimentacao.ENTRADA, 1, null, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("apenas a peças");
    }

    @Test
    @DisplayName("Deve lançar exceção ao dar saída com saldo insuficiente")
    void deveLancarExcecaoSaldoInsuficiente() {
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        when(estoqueGateway.obterSaldoAtual(1L)).thenReturn(2);

        assertThatThrownBy(() -> produtoUseCase.registrarMovimentacao(1L, TipoMovimentacao.SAIDA, 5, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    @DisplayName("Deve inativar produto")
    void deveInativarProduto() {
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));

        produtoUseCase.inativar(1L);

        verify(produtoGateway).salvar(any(Produto.class));
        assertThat(peca.getAtivo()).isFalse();
    }

    @Test
    @DisplayName("Deve listar produtos por tipo")
    void deveListarPorTipo() {
        when(produtoGateway.listarPorTipo(TipoProduto.PECA)).thenReturn(List.of(peca));

        List<Produto> lista = produtoUseCase.listarPorTipo(TipoProduto.PECA);

        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar produto inexistente")
    void deveLancarExcecaoProdutoInexistente() {
        when(produtoGateway.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoUseCase.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
