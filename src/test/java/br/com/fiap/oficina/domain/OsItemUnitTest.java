package br.com.fiap.oficina.domain;

import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.model.Produto;
import br.com.fiap.oficina.domain.model.Veiculo;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.domain.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.domain.repository.OrdemServicoRepository;
import br.com.fiap.oficina.domain.repository.ProdutoRepository;
import br.com.fiap.oficina.domain.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.service.ClienteService;
import br.com.fiap.oficina.service.OrdemServicoService;
import br.com.fiap.oficina.service.ProdutoService;
import br.com.fiap.oficina.service.VeiculoService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OsItemUnitTest {

    @Mock OrdemServicoRepository osRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock VeiculoRepository veiculoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Mock ProdutoService produtoService;
    @Mock ClienteService clienteService;
    @Mock VeiculoService veiculoService;
    @InjectMocks OrdemServicoService osService;

    private Cliente cliente;
    private Veiculo veiculo;

    @BeforeEach
    void setup() {
        cliente = Cliente.builder().id(1L).nome("Ana").cpfCnpj("12345678901")
                .tipoDocumento(TipoDocumento.CPF).criadoEm(LocalDateTime.now()).build();
        veiculo = Veiculo.builder().id(1L).placa("ABC1234").marca("Toyota")
                .modelo("Corolla").ano(2020).criadoEm(LocalDateTime.now()).build();
    }

    private void mockBase(Produto produto) {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(osRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();
    }

    @Test
    @DisplayName("Serviço deve calcular subtotal de item (qty * preço)")
    void deveCalcularSubtotalDeItem() {
        Produto produto = Produto.builder().id(2L).nome("Pastilha de Freio").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("120.00")).ativo(true).build();
        mockBase(produto);

        OrdemServicoRequest.OsItemRequest item =
                new OrdemServicoRequest.OsItemRequest(2L, 4, new BigDecimal("120.00"), null);
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 1L, "Freios", null, List.of(item));

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response.valorTotal()).isEqualByComparingTo(new BigDecimal("480.00"));
    }

    @Test
    @DisplayName("Serviço deve calcular subtotal com múltiplas unidades")
    void deveCalcularSubtotalMultiplasUnidades() {
        Produto produto = Produto.builder().id(2L).nome("Oleo 5W30").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("28.00")).ativo(true).build();
        mockBase(produto);

        OrdemServicoRequest.OsItemRequest item =
                new OrdemServicoRequest.OsItemRequest(2L, 3, new BigDecimal("28.00"), null);
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 1L, "Troca de oleo", null, List.of(item));

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response.valorTotal()).isEqualByComparingTo(new BigDecimal("84.00"));
    }

    @Test
    @DisplayName("Serviço deve somar subtotais de múltiplos itens")
    void deveSomarSubtotaisDeMultiplosItens() {
        Produto produto = Produto.builder().id(2L).nome("Peça").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("50.00")).ativo(true).build();
        mockBase(produto);

        OrdemServicoRequest.OsItemRequest item1 =
                new OrdemServicoRequest.OsItemRequest(2L, 2, new BigDecimal("50.00"), null);
        OrdemServicoRequest.OsItemRequest item2 =
                new OrdemServicoRequest.OsItemRequest(2L, 1, new BigDecimal("200.00"), null);
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 1L, "Revisão", null, List.of(item1, item2));

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response.valorTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Serviço deve usar preço do produto quando item não informa preço")
    void deveUsarPrecoDoProdutoQuandoItemNaoInformaPreco() {
        Produto produto = Produto.builder().id(2L).nome("Filtro de Ar").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("35.00")).ativo(true).build();
        mockBase(produto);

        OrdemServicoRequest.OsItemRequest item =
                new OrdemServicoRequest.OsItemRequest(2L, 2, null, null);
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 1L, "Manutenção", null, List.of(item));

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response.valorTotal()).isEqualByComparingTo(new BigDecimal("70.00"));
    }
}
