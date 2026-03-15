package br.com.fiap.oficina.domain;

import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.MovimentacaoEstoque;
import br.com.fiap.oficina.domain.model.Produto;
import br.com.fiap.oficina.domain.model.SaldoEstoque;
import br.com.fiap.oficina.domain.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.domain.repository.OrdemServicoRepository;
import br.com.fiap.oficina.domain.repository.ProdutoRepository;
import br.com.fiap.oficina.domain.repository.SaldoEstoqueRepository;
import br.com.fiap.oficina.dto.request.MovimentacaoEstoqueRequest;
import br.com.fiap.oficina.dto.response.MovimentacaoEstoqueResponse;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import br.com.fiap.oficina.service.ProdutoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaldoEstoqueUnitTest {

    @Mock ProdutoRepository produtoRepository;
    @Mock SaldoEstoqueRepository saldoEstoqueRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Mock OrdemServicoRepository ordemServicoRepository;
    @InjectMocks ProdutoService produtoService;

    private Produto peca;
    private SaldoEstoque saldo;

    @BeforeEach
    void setup() {
        peca = Produto.builder().id(1L).nome("Filtro").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("45.00")).ativo(true).criadoEm(LocalDateTime.now()).build();
        saldo = SaldoEstoque.builder().id(1L).produto(peca).quantidade(BigDecimal.ZERO).build();
        peca.setSaldoEstoque(saldo);
    }

    @Test
    @DisplayName("Serviço deve registrar entrada e aumentar saldo")
    void deveRegistrarEntradaEAumentarSaldo() {
        MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest(
                1L, TipoMovimentacao.ENTRADA, new BigDecimal("10"), "Compra NF-001", null);
        MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                .id(1L).produto(peca).tipo(TipoMovimentacao.ENTRADA)
                .quantidade(new BigDecimal("10")).criadoEm(LocalDateTime.now()).build();

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(movimentacaoEstoqueRepository.save(any())).thenReturn(mov);
        when(saldoEstoqueRepository.findByProdutoId(1L)).thenReturn(Optional.of(saldo));
        when(movimentacaoEstoqueRepository.calcularSaldoPorProduto(1L)).thenReturn(new BigDecimal("10"));
        when(saldoEstoqueRepository.save(any())).thenReturn(saldo);

        MovimentacaoEstoqueResponse response = produtoService.registrarMovimentacao(request);

        assertThat(response.tipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(saldo.getQuantidade()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("Serviço deve registrar saída e diminuir saldo")
    void deveRegistrarSaidaEDiminuirSaldo() {
        saldo.setQuantidade(new BigDecimal("10"));
        MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("3"), "Uso em OS", null);
        MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                .id(2L).produto(peca).tipo(TipoMovimentacao.SAIDA)
                .quantidade(new BigDecimal("3")).criadoEm(LocalDateTime.now()).build();

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(saldoEstoqueRepository.findByProdutoId(1L)).thenReturn(Optional.of(saldo));
        when(movimentacaoEstoqueRepository.save(any())).thenReturn(mov);
        when(movimentacaoEstoqueRepository.calcularSaldoPorProduto(1L)).thenReturn(new BigDecimal("7"));
        when(saldoEstoqueRepository.save(any())).thenReturn(saldo);

        MovimentacaoEstoqueResponse response = produtoService.registrarMovimentacao(request);

        assertThat(response.tipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(saldo.getQuantidade()).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    @DisplayName("Serviço deve lançar exceção quando saldo insuficiente para saída")
    void deveLancarExcecaoSaldoInsuficiente() {
        saldo.setQuantidade(new BigDecimal("2"));
        MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest(
                1L, TipoMovimentacao.SAIDA, new BigDecimal("5"), "Uso em OS", null);

        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(saldoEstoqueRepository.findByProdutoId(1L)).thenReturn(Optional.of(saldo));

        assertThatThrownBy(() -> produtoService.registrarMovimentacao(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    @DisplayName("Serviço deve lançar exceção ao movimentar produto do tipo SERVICO")
    void deveLancarExcecaoMovimentarServico() {
        Produto servico = Produto.builder().id(2L).nome("Diagnóstico").tipo(TipoProduto.SERVICO)
                .precoUnitario(new BigDecimal("120.00")).ativo(true).build();
        MovimentacaoEstoqueRequest request = new MovimentacaoEstoqueRequest(
                2L, TipoMovimentacao.ENTRADA, new BigDecimal("1"), null, null);

        when(produtoRepository.findById(2L)).thenReturn(Optional.of(servico));

        assertThatThrownBy(() -> produtoService.registrarMovimentacao(request))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("peças");
    }

    @Test
    @DisplayName("Serviço deve sincronizar saldo com total calculado das movimentações")
    void deveSincronizarSaldoComTotalMovimentacoes() {
        when(saldoEstoqueRepository.findByProdutoId(1L)).thenReturn(Optional.of(saldo));
        when(movimentacaoEstoqueRepository.calcularSaldoPorProduto(1L)).thenReturn(new BigDecimal("15"));
        when(saldoEstoqueRepository.save(any())).thenReturn(saldo);

        produtoService.sincronizarSaldo(1L);

        assertThat(saldo.getQuantidade()).isEqualByComparingTo(new BigDecimal("15"));
    }
}
