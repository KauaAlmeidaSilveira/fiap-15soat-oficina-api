package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.enums.StatusOS;
import br.com.fiap.oficina.domain.enums.TipoDocumento;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.model.OrdemServico;
import br.com.fiap.oficina.domain.model.Produto;
import br.com.fiap.oficina.domain.model.Veiculo;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.domain.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.domain.repository.OrdemServicoRepository;
import br.com.fiap.oficina.domain.repository.ProdutoRepository;
import br.com.fiap.oficina.domain.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.handler.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import jakarta.persistence.EntityManager;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoServiceUnitTest {

    @Mock OrdemServicoRepository osRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock VeiculoRepository veiculoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Mock ProdutoService produtoService;
    @Mock EntityManager entityManager;
    @Mock ClienteService clienteService;
    @Mock VeiculoService veiculoService;
    @InjectMocks OrdemServicoService osService;

    private Cliente cliente;
    private Veiculo veiculo;
    private OrdemServico os;
    private Produto peca;

    @BeforeEach
    void setup() {
        cliente = Cliente.builder().id(1L).nome("João").cpfCnpj("12345678901")
                .tipoDocumento(TipoDocumento.CPF).criadoEm(LocalDateTime.now()).build();

        veiculo = Veiculo.builder().id(1L).placa("ABC1234").marca("Toyota")
                .modelo("Corolla").ano(2020).criadoEm(LocalDateTime.now()).build();

        peca = Produto.builder().id(1L).nome("Filtro").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("50.00")).ativo(true).build();

        os = OrdemServico.builder()
                .id(1L).numero("OS1234567890")
                .cliente(cliente).veiculo(veiculo)
                .status(StatusOS.RECEBIDA)
                .valorTotal(BigDecimal.ZERO)
                .criadoEm(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve criar OS com sucesso")
    void deveCriarOS() {
        OrdemServicoRequest request = new OrdemServicoRequest(
                1L, 1L, "Barulho no motor", null, null);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        when(osRepository.save(any(OrdemServico.class))).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        OrdemServicoResponse response = osService.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(StatusOS.RECEBIDA);
        verify(osRepository).save(any(OrdemServico.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando cliente não encontrado")
    void deveLancarExcecaoClienteNaoEncontrado() {
        OrdemServicoRequest request = new OrdemServicoRequest(99L, 1L, null, null, null);
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> osService.criar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando veículo não encontrado")
    void deveLancarExcecaoVeiculoNaoEncontrado() {
        OrdemServicoRequest request = new OrdemServicoRequest(1L, 99L, null, null, null);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> osService.criar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve avançar status da OS")
    void deveAvancarStatus() {
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        OrdemServicoResponse response = osService.avancarStatus(1L);

        assertThat(response.status()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
    }

    @Test
    @DisplayName("Deve aprovar OS em status AGUARDANDO_APROVACAO")
    void deveAprovarOS() {
        os.setStatus(StatusOS.AGUARDANDO_APROVACAO);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        OrdemServicoResponse response = osService.aprovar(1L);

        assertThat(response.status()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getDataAprovacao()).isNotNull();
        assertThat(os.getDataInicio()).isNotNull();
    }

    @Test
    @DisplayName("Deve lançar exceção ao aprovar OS com status diferente de AGUARDANDO_APROVACAO")
    void deveLancarExcecaoAprovarStatusErrado() {
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> osService.aprovar(1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("AGUARDANDO_APROVACAO");
    }

    @Test
    @DisplayName("Deve adicionar item à OS")
    void deveAdicionarItemOS() {
        OrdemServicoRequest.OsItemRequest itemReq = new OrdemServicoRequest.OsItemRequest(
                1L, 2, null, null);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.adicionarItem(1L, itemReq);

        assertThat(os.getItens()).hasSize(1);
    }

    @Test
    @DisplayName("Deve lançar exceção ao adicionar item em OS finalizada")
    void deveLancarExcecaoAdicionarItemOSFinalizada() {
        os.setStatus(StatusOS.FINALIZADA);
        OrdemServicoRequest.OsItemRequest itemReq = new OrdemServicoRequest.OsItemRequest(
                1L, 1, null, null);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> osService.adicionarItem(1L, itemReq))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("finalizada");
    }

    @Test
    @DisplayName("Deve listar OS por status")
    void deveListarPorStatus() {
        when(osRepository.findByStatus(StatusOS.RECEBIDA)).thenReturn(List.of(os));
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        List<OrdemServicoResponse> lista = osService.listarPorStatus(StatusOS.RECEBIDA);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).status()).isEqualTo(StatusOS.RECEBIDA);
    }
}
