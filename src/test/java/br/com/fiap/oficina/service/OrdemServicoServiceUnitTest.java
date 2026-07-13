package br.com.fiap.oficina.service;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico;
import br.com.fiap.oficina.dataprovider.persistence.entity.Produto;
import br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.dataprovider.persistence.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.dataprovider.persistence.entity.OsItem;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OrdemServicoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OsItemRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.AprovarOsRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.NotificacaoAprovacaoGateway;
import br.com.fiap.oficina.core.gateway.TokenAprovacaoGateway;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    @Mock TokenAprovacaoGateway aprovacaoTokenService;
    @Mock NotificacaoAprovacaoGateway notificacaoAprovacaoService;
    @InjectMocks OrdemServicoService osService;

    private Cliente cliente;
    private Veiculo veiculo;
    private OrdemServico os;
    private Produto peca;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(osService, "publicBaseUrl", "http://localhost:8080");

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
        verify(notificacaoAprovacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("Deve notificar cliente por e-mail ao avançar para AGUARDANDO_APROVACAO")
    void deveNotificarClienteAoEntrarEmAguardandoAprovacao() {
        os.setStatus(StatusOS.EM_DIAGNOSTICO);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();
        when(aprovacaoTokenService.gerarToken(1L, true)).thenReturn("token-aprovar");
        when(aprovacaoTokenService.gerarToken(1L, false)).thenReturn("token-recusar");

        OrdemServicoResponse response = osService.avancarStatus(1L);

        var dadosEsperados = new NotificacaoAprovacaoGateway.Dados(
                "OS1234567890", "João", null, "Toyota", "Corolla", "ABC1234", List.of(), BigDecimal.ZERO);

        assertThat(response.status()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        verify(notificacaoAprovacaoService).notificar(
                dadosEsperados,
                "http://localhost:8080/aprovacao-os?token=token-aprovar",
                "http://localhost:8080/aprovacao-os?token=token-recusar");
    }

    @Test
    @DisplayName("Falha ao notificar não deve impedir o avanço de status")
    void falhaAoNotificarNaoDeveQuebrarAvancoDeStatus() {
        os.setStatus(StatusOS.EM_DIAGNOSTICO);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();
        when(aprovacaoTokenService.gerarToken(any(), anyBoolean())).thenThrow(new RuntimeException("SMTP indisponível"));

        OrdemServicoResponse response = osService.avancarStatus(1L);

        assertThat(response.status()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
    }

    @Test
    @DisplayName("Deve aprovar OS em status AGUARDANDO_APROVACAO")
    void deveAprovarOS() {
        os.setStatus(StatusOS.AGUARDANDO_APROVACAO);

        when(osRepository.findById(1L)).thenReturn(Optional.of(os));
        when(osRepository.save(any())).thenReturn(os);
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        OrdemServicoResponse response = osService.aprovar(1L, new AprovarOsRequest(true));

        assertThat(response.status()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getDataAprovacao()).isNotNull();
        assertThat(os.getDataInicio()).isNotNull();
    }

    @Test
    @DisplayName("Deve lançar exceção ao aprovar OS com status diferente de AGUARDANDO_APROVACAO")
    void deveLancarExcecaoAprovarStatusErrado() {
        when(osRepository.findById(1L)).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> osService.aprovar(1L, new AprovarOsRequest(true)))
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
    @DisplayName("Deve listar apenas OS ativas, ordenadas por status e depois por mais antiga primeiro")
    void deveListarTodasOrdenadaEExcluindoFinalizadas() {
        LocalDateTime agora = LocalDateTime.now();

        OrdemServico recebidaAntiga = OrdemServico.builder().id(1L).numero("OS1")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.RECEBIDA)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(3)).build();
        OrdemServico recebidaNova = OrdemServico.builder().id(2L).numero("OS2")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.RECEBIDA)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(1)).build();
        OrdemServico emDiagnostico = OrdemServico.builder().id(3L).numero("OS3")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.EM_DIAGNOSTICO)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(2)).build();
        OrdemServico aguardandoAprovacao = OrdemServico.builder().id(4L).numero("OS4")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.AGUARDANDO_APROVACAO)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(4)).build();
        OrdemServico emExecucao = OrdemServico.builder().id(5L).numero("OS5")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.EM_EXECUCAO)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(5)).build();

        // não devem aparecer na listagem padrão
        OrdemServico finalizada = OrdemServico.builder().id(6L).numero("OS6")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.FINALIZADA)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(10)).build();
        OrdemServico entregue = OrdemServico.builder().id(7L).numero("OS7")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.ENTREGUE)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(11)).build();
        OrdemServico reprovada = OrdemServico.builder().id(8L).numero("OS8")
                .cliente(cliente).veiculo(veiculo).status(StatusOS.REPROVADA)
                .valorTotal(BigDecimal.ZERO).criadoEm(agora.minusHours(12)).build();

        when(osRepository.findByStatusNotIn(List.of(StatusOS.FINALIZADA, StatusOS.ENTREGUE, StatusOS.REPROVADA)))
                .thenReturn(List.of(recebidaAntiga, recebidaNova, emDiagnostico, aguardandoAprovacao, emExecucao));
        when(clienteService.toResponse(any())).thenCallRealMethod();
        when(veiculoService.toResponse(any())).thenCallRealMethod();

        List<OrdemServicoResponse> lista = osService.listarTodas();

        assertThat(lista).hasSize(5);
        assertThat(lista.stream().map(OrdemServicoResponse::numero))
                .containsExactly("OS5", "OS4", "OS3", "OS1", "OS2");
        assertThat(lista.stream().map(OrdemServicoResponse::status))
                .doesNotContain(StatusOS.FINALIZADA, StatusOS.ENTREGUE, StatusOS.REPROVADA);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando todas as OS estão finalizadas, entregues ou reprovadas")
    void deveRetornarListaVaziaQuandoTodasExcluidas() {
        when(osRepository.findByStatusNotIn(List.of(StatusOS.FINALIZADA, StatusOS.ENTREGUE, StatusOS.REPROVADA)))
                .thenReturn(List.of());

        List<OrdemServicoResponse> lista = osService.listarTodas();

        assertThat(lista).isEmpty();
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
