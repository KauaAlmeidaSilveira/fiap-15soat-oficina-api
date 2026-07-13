package br.com.fiap.oficina.service;

import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.dataprovider.persistence.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.dataprovider.persistence.entity.Produto;
import br.com.fiap.oficina.dataprovider.persistence.entity.SaldoEstoque;
import br.com.fiap.oficina.dataprovider.persistence.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OrdemServicoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.SaldoEstoqueRepository;
import br.com.fiap.oficina.dto.request.MovimentacaoEstoqueRequest;
import br.com.fiap.oficina.dto.request.ProdutoRequest;
import br.com.fiap.oficina.dto.response.MovimentacaoEstoqueResponse;
import br.com.fiap.oficina.dto.response.ProdutoResponse;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceUnitTest {

    @Mock ProdutoRepository produtoRepository;
    @Mock SaldoEstoqueRepository saldoEstoqueRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Mock OrdemServicoRepository ordemServicoRepository;
    @InjectMocks ProdutoService produtoService;

    private Produto peca;
    private Produto servico;
    private SaldoEstoque saldo;

    @BeforeEach
    void setup() {
        peca = Produto.builder()
                .id(1L)
                .nome("Filtro de Óleo")
                .tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("45.00"))
                .ativo(true)
                .criadoEm(LocalDateTime.now())
                .build();

        servico = Produto.builder()
                .id(2L)
                .nome("Troca de Óleo")
                .tipo(TipoProduto.SERVICO)
                .precoUnitario(new BigDecimal("80.00"))
                .ativo(true)
                .criadoEm(LocalDateTime.now())
                .build();

        saldo = SaldoEstoque.builder()
                .id(1L)
                .produto(peca)
                .quantidade(10)
                .build();
        peca.setSaldoEstoque(saldo);
    }

    @Test
    @DisplayName("Deve criar peça e inicializar saldo de estoque")
    void deveCriarPecaComSaldoEstoque() {
        ProdutoRequest request = new ProdutoRequest(
                "Filtro de Óleo", "Filtro original", TipoProduto.PECA,
                new BigDecimal("45.00"), "UN", null);

        when(produtoRepository.save(any(Produto.class))).thenReturn(peca);
        when(saldoEstoqueRepository.save(any(SaldoEstoque.class))).thenReturn(saldo);

        ProdutoResponse response = produtoService.criar(request);

        assertThat(response.nome()).isEqualTo("Filtro de Óleo");
        assertThat(response.tipo()).isEqualTo(TipoProduto.PECA);
        verify(saldoEstoqueRepository).save(any(SaldoEstoque.class));
    }

    @Test
    @DisplayName("Deve criar serviço SEM inicializar saldo de estoque")
    void deveCriarServicoSemSaldoEstoque() {
        ProdutoRequest request = new ProdutoRequest(
                "Troca de Óleo", null, TipoProduto.SERVICO,
                new BigDecimal("80.00"), null, null);

        when(produtoRepository.save(any(Produto.class))).thenReturn(servico);

        produtoService.criar(request);

        verify(saldoEstoqueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve registrar entrada de estoque corretamente")
    void deveRegistrarEntradaEstoque() {
        MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest(
                1L, TipoMovimentacao.ENTRADA,
                5, "Compra NF 001", null);

        MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                .id(1L).produto(peca).tipo(TipoMovimentacao.ENTRADA)
                .quantidade(5).criadoEm(LocalDateTime.now()).build();

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(saldoEstoqueRepository.findByProdutoId(1L)).thenReturn(Optional.of(saldo));
        when(saldoEstoqueRepository.save(any())).thenReturn(saldo);
        when(movimentacaoEstoqueRepository.save(any())).thenReturn(mov);
        when(movimentacaoEstoqueRepository.calcularSaldoPorProduto(1L)).thenReturn(15);

        MovimentacaoEstoqueResponse response = produtoService.registrarMovimentacao(request);

        assertThat(response.tipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(saldo.getQuantidade()).isEqualTo(15);
    }

    @Test
    @DisplayName("Deve lançar exceção ao movimentar estoque de serviço")
    void deveLancarExcecaoMovimentacaoServico() {
        MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest(
                2L, TipoMovimentacao.ENTRADA,
                5, null, null);

        when(produtoRepository.findById(2L)).thenReturn(Optional.of(servico));

        assertThatThrownBy(() -> produtoService.registrarMovimentacao(request))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("peças");
    }

    @Test
    @DisplayName("Deve inativar produto")
    void deveInativarProduto() {
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(produtoRepository.save(any())).thenReturn(peca);

        produtoService.inativar(1L);

        assertThat(peca.getAtivo()).isFalse();
        verify(produtoRepository).save(peca);
    }

    @Test
    @DisplayName("Deve listar produtos por tipo")
    void deveListarPorTipo() {
        when(produtoRepository.findByTipo(TipoProduto.PECA)).thenReturn(List.of(peca));

        List<ProdutoResponse> lista = produtoService.listarPorTipo(TipoProduto.PECA);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).tipo()).isEqualTo(TipoProduto.PECA);
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar produto inexistente")
    void deveLancarExcecaoProdutoInexistente() {
        when(produtoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
