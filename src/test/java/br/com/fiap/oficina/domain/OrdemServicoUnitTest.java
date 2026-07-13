package br.com.fiap.oficina.domain;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico;
import br.com.fiap.oficina.dataprovider.persistence.entity.Produto;
import br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OrdemServicoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoUnitTest {

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
    private OrdemServico os;

    @BeforeEach
    void setup() {
        cliente = Cliente.builder().id(1L).nome("João").cpfCnpj("12345678901")
                .tipoDocumento(TipoDocumento.CPF).criadoEm(LocalDateTime.now()).build();
        veiculo = Veiculo.builder().id(1L).placa("ABC1234").marca("Toyota")
                .modelo("Corolla").ano(2020).criadoEm(LocalDateTime.now()).build();
        os = OrdemServico.builder()
                .id(1L).numero("OS001")
                .cliente(cliente).veiculo(veiculo)
                .status(StatusOS.RECEBIDA)
                .itens(new ArrayList<>())
                .valorTotal(BigDecimal.ZERO)
                .criadoEm(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("OS criada pelo serviço deve ter status inicial RECEBIDA")
    void statusInicialDeveSerRecebida() {
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 1L, "Motor falhando", null, null);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response.status()).isEqualTo(StatusOS.RECEBIDA);
    }

    @Test
    @DisplayName("Serviço deve avançar status sequencialmente")
    void deveAvancarStatusSequencial() {
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.avancarStatus(1L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);

        osService.avancarStatus(1L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);

        osService.avancarStatus(1L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);

        osService.avancarStatus(1L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.FINALIZADA);

        osService.avancarStatus(1L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.ENTREGUE);
    }

    @Test
    @DisplayName("Serviço deve lançar exceção ao tentar avançar OS já entregue")
    void deveLancarExcecaoOsEntregue() {
        os.setStatus(StatusOS.ENTREGUE);
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> osService.avancarStatus(1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("entregue");
    }

    @Test
    @DisplayName("Serviço deve calcular valor total ao criar OS com múltiplos itens")
    void deveCalcularTotalComItens() {
        Produto produto = Produto.builder().id(2L).nome("Óleo").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("50.00")).ativo(true).build();

        OrdemServicoRequest.OsItemRequest item1 =
                new OrdemServicoRequest.OsItemRequest(2L, 2, new BigDecimal("50.00"), null);
        OrdemServicoRequest.OsItemRequest item2 =
                new OrdemServicoRequest.OsItemRequest(2L, 1, new BigDecimal("200.00"), null);
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 1L, "Revisão", null, List.of(item1, item2));

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(produtoRepository.findById(2L)).thenReturn(Optional.of(produto));
        when(osRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response.valorTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
    }
}
