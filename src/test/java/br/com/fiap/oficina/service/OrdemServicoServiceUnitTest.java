package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.enums.StatusOS;
import br.com.fiap.oficina.domain.enums.TipoDocumento;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.model.OrdemServico;
import br.com.fiap.oficina.domain.model.Produto;
import br.com.fiap.oficina.domain.model.Veiculo;
import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.domain.model.MovimentacaoEstoque;
import br.com.fiap.oficina.domain.model.OsItem;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.domain.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.domain.repository.OrdemServicoRepository;
import br.com.fiap.oficina.domain.repository.OsItemRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoServiceUnitTest {

    @Mock OrdemServicoRepository osRepository;
    @Mock OsItemRepository osItemRepository;
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
    @DisplayName("Deve registrar SAIDA ao adicionar PECA em OS já aprovada")
    void deveRegistrarSaidaAoAdicionarPecaEmOSAprovada() {
        os.setDataAprovacao(LocalDateTime.now());
        OrdemServicoRequest.OsItemRequest itemReq = new OrdemServicoRequest.OsItemRequest(1L, 3, null, null);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(movimentacaoEstoqueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.adicionarItem(1L, itemReq);

        verify(movimentacaoEstoqueRepository).save(any(MovimentacaoEstoque.class));
        verify(produtoService).sincronizarSaldo(peca.getId());
    }

    @Test
    @DisplayName("Deve adicionar SERVICO em OS aprovada sem afetar estoque")
    void deveAdicionarServicoEmOSAprovadaSemAfetarEstoque() {
        Produto servico = Produto.builder().id(2L).nome("Balanceamento")
                .tipo(TipoProduto.SERVICO).precoUnitario(new BigDecimal("60.00")).ativo(true).build();
        os.setDataAprovacao(LocalDateTime.now());
        OrdemServicoRequest.OsItemRequest itemReq = new OrdemServicoRequest.OsItemRequest(2L, 1, null, null);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(produtoRepository.findById(2L)).thenReturn(Optional.of(servico));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.adicionarItem(1L, itemReq);

        verify(movimentacaoEstoqueRepository, never()).save(any());
        verify(produtoService, never()).sincronizarSaldo(any());
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

    @Test
    @DisplayName("Deve remover item de OS não aprovada sem afetar estoque")
    void deveRemoverItemDeOSNaoAprovada() {
        OsItem item = OsItem.builder().id(10L).produto(peca)
                .quantidade(2).precoUnitario(new BigDecimal("50.00")).build();
        os.getItens().add(item);
        os.setValorTotal(new BigDecimal("100.00"));

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osItemRepository.findByIdAndOrdemServicoId(10L, 1L)).thenReturn(Optional.of(item));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.removerItem(1L, 10L);

        assertThat(os.getItens()).isEmpty();
        assertThat(os.getValorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(movimentacaoEstoqueRepository, never()).save(any());
        verify(produtoService, never()).sincronizarSaldo(any());
    }

    @Test
    @DisplayName("Deve remover item de OS aprovada com PECA e estornar estoque")
    void deveRemoverItemDeOSAprovadaComPecaEstornaEstoque() {
        os.setDataAprovacao(LocalDateTime.now());
        OsItem item = OsItem.builder().id(10L).produto(peca)
                .quantidade(3).precoUnitario(new BigDecimal("50.00")).build();
        os.getItens().add(item);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osItemRepository.findByIdAndOrdemServicoId(10L, 1L)).thenReturn(Optional.of(item));
        when(movimentacaoEstoqueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.removerItem(1L, 10L);

        assertThat(os.getItens()).isEmpty();
        verify(movimentacaoEstoqueRepository).save(any(MovimentacaoEstoque.class));
        verify(produtoService).sincronizarSaldo(peca.getId());
    }

    @Test
    @DisplayName("Deve remover item de OS aprovada com SERVICO sem afetar estoque")
    void deveRemoverItemDeOSAprovadaComServicoNaoAfetaEstoque() {
        Produto servico = Produto.builder().id(2L).nome("Alinhamento")
                .tipo(TipoProduto.SERVICO).precoUnitario(new BigDecimal("80.00")).ativo(true).build();
        os.setDataAprovacao(LocalDateTime.now());
        OsItem item = OsItem.builder().id(11L).produto(servico)
                .quantidade(1).precoUnitario(new BigDecimal("80.00")).build();
        os.getItens().add(item);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osItemRepository.findByIdAndOrdemServicoId(11L, 1L)).thenReturn(Optional.of(item));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.removerItem(1L, 11L);

        assertThat(os.getItens()).isEmpty();
        verify(movimentacaoEstoqueRepository, never()).save(any());
        verify(produtoService, never()).sincronizarSaldo(any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao remover item que não pertence à OS")
    void deveLancarExcecaoAoRemoverItemNaoEncontradoNaOS() {
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osItemRepository.findByIdAndOrdemServicoId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> osService.removerItem(1L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Ciclo completo: adiciona PECA pós-aprovação (SAIDA) e depois remove (ENTRADA) — duas movimentações, resultado líquido zero")
    void deveRegistrarSaidaEEntradaAoCicloAdicionarRemoverPecaAposAprovacao() {
        os.setDataAprovacao(LocalDateTime.now());
        OrdemServicoRequest.OsItemRequest itemReq = new OrdemServicoRequest.OsItemRequest(1L, 2, null, null);

        // — passo 1: adicionar item pós-aprovação
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(peca));
        when(movimentacaoEstoqueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        osService.adicionarItem(1L, itemReq);

        assertThat(os.getItens()).hasSize(1);
        verify(movimentacaoEstoqueRepository).save(any(MovimentacaoEstoque.class)); // SAIDA

        OsItem itemAdicionado = os.getItens().get(0);

        // — passo 2: remover o mesmo item
        when(osItemRepository.findByIdAndOrdemServicoId(itemAdicionado.getId(), 1L))
                .thenReturn(Optional.of(itemAdicionado));

        osService.removerItem(1L, itemAdicionado.getId());

        assertThat(os.getItens()).isEmpty();
        // duas chamadas ao save de movimentação: uma SAIDA + uma ENTRADA
        verify(movimentacaoEstoqueRepository, org.mockito.Mockito.times(2)).save(any(MovimentacaoEstoque.class));
        verify(produtoService, org.mockito.Mockito.times(2)).sincronizarSaldo(peca.getId());
    }
}
