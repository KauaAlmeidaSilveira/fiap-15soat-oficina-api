package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.EstoqueGateway;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import br.com.fiap.oficina.core.gateway.NotificacaoAprovacaoGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.gateway.TokenAprovacaoGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase.ItemNovo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdemServicoUseCaseUnitTest {

    @Mock OrdemServicoGateway osGateway;
    @Mock ClienteGateway clienteGateway;
    @Mock VeiculoGateway veiculoGateway;
    @Mock ProdutoGateway produtoGateway;
    @Mock EstoqueGateway estoqueGateway;
    @Mock TokenAprovacaoGateway tokenGateway;
    @Mock NotificacaoAprovacaoGateway notificacaoGateway;
    @Mock MetricasGateway metricasGateway;

    @Captor ArgumentCaptor<Duration> duracaoCaptor;

    OrdemServicoUseCase useCase;

    private Cliente cliente;
    private Veiculo veiculo;
    private Produto peca;
    private Produto servico;
    private OrdemServico os;

    @BeforeEach
    void setup() {
        useCase = new OrdemServicoUseCase(osGateway, clienteGateway, veiculoGateway, produtoGateway,
                estoqueGateway, tokenGateway, notificacaoGateway, metricasGateway, "http://localhost:8080");

        cliente = Cliente.builder().id(1L).nome("João").status(StatusCliente.ATIVO).build();
        veiculo = Veiculo.builder().id(1L).placa("ABC1234").marca("Toyota").modelo("Corolla").ano(2020).build();
        peca = Produto.builder().id(1L).nome("Filtro").tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal("50.00")).ativo(true).build();
        servico = Produto.builder().id(2L).nome("Balanceamento").tipo(TipoProduto.SERVICO)
                .precoUnitario(new BigDecimal("60.00")).ativo(true).build();
        os = OrdemServico.builder()
                .id(1L).numero("OS1234567890").cliente(cliente).veiculo(veiculo)
                .status(StatusOS.RECEBIDA).valorTotal(BigDecimal.ZERO)
                .itens(new ArrayList<>()).criadoEm(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Deve criar OS com status inicial RECEBIDA")
    void deveCriarComStatusRecebida() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(cliente));
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculo));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdemServico criada = useCase.criar(1L, 1L, "Motor falhando", null, List.of());

        assertThat(criada.getStatus()).isEqualTo(StatusOS.RECEBIDA);
    }

    @Test
    @DisplayName("Deve calcular total ao criar OS com múltiplos itens")
    void deveCalcularTotalComItens() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(cliente));
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculo));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdemServico criada = useCase.criar(1L, 1L, "Revisão", null, List.of(
                new ItemNovo(1L, 2, new BigDecimal("50.00"), null),
                new ItemNovo(1L, 1, new BigDecimal("200.00"), null)));

        assertThat(criada.getValorTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Não deve abrir OS para cliente inativo")
    void naoDeveCriarOsParaClienteInativo() {
        cliente.inativar();
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> useCase.criar(1L, 1L, "Motor falhando", null, List.of()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar com cliente inexistente")
    void deveLancarExcecaoClienteInexistente() {
        when(clienteGateway.buscarPorId(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.criar(99L, 1L, null, null, List.of()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve avançar status sequencialmente")
    void deveAvancarStatus() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(useCase.avancarStatus(1L).getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
    }

    @Test
    @DisplayName("Deve lançar exceção ao avançar OS entregue")
    void deveLancarExcecaoAvancarEntregue() {
        os.setStatus(StatusOS.ENTREGUE);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        assertThatThrownBy(() -> useCase.avancarStatus(1L))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("entregue");
    }

    @Test
    @DisplayName("Deve notificar cliente ao entrar em AGUARDANDO_APROVACAO")
    void deveNotificarAoEntrarEmAguardandoAprovacao() {
        os.setStatus(StatusOS.EM_DIAGNOSTICO);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGateway.gerarToken(1L, true)).thenReturn("token-aprovar");
        when(tokenGateway.gerarToken(1L, false)).thenReturn("token-recusar");

        useCase.avancarStatus(1L);

        var dadosEsperados = new NotificacaoAprovacaoGateway.Dados(
                "OS1234567890", "João", null, "Toyota", "Corolla", "ABC1234", List.of(), BigDecimal.ZERO);
        verify(notificacaoGateway).notificar(dadosEsperados,
                "http://localhost:8080/aprovacao-os?token=token-aprovar",
                "http://localhost:8080/aprovacao-os?token=token-recusar");
    }

    @Test
    @DisplayName("Falha ao notificar não deve impedir o avanço de status")
    void falhaAoNotificarNaoQuebraAvanco() {
        os.setStatus(StatusOS.EM_DIAGNOSTICO);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGateway.gerarToken(any(), anyBoolean())).thenThrow(new RuntimeException("SMTP indisponível"));

        OrdemServico resultado = useCase.avancarStatus(1L);

        assertThat(resultado.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
    }

    @Test
    @DisplayName("Deve aprovar OS em AGUARDANDO_APROVACAO")
    void deveAprovarOS() {
        os.setStatus(StatusOS.AGUARDANDO_APROVACAO);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        OrdemServico aprovada = useCase.aprovar(1L, true);

        assertThat(aprovada.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(aprovada.getDataAprovacao()).isNotNull();
    }

    @Test
    @DisplayName("Deve lançar exceção ao aprovar OS com status errado")
    void deveLancarExcecaoAprovarStatusErrado() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        assertThatThrownBy(() -> useCase.aprovar(1L, true))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("AGUARDANDO_APROVACAO");
    }

    @Test
    @DisplayName("Deve processar decisão via token")
    void deveProcessarDecisaoViaToken() {
        os.setStatus(StatusOS.AGUARDANDO_APROVACAO);
        when(tokenGateway.validar("tk")).thenReturn(new TokenAprovacaoGateway.TokenAprovacao(1L, true));
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(useCase.processarDecisaoViaToken("tk")).isTrue();
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
    }

    @Test
    @DisplayName("Deve adicionar item à OS")
    void deveAdicionarItem() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.adicionarItem(1L, new ItemNovo(1L, 2, null, null));

        assertThat(os.getItens()).hasSize(1);
    }

    @Test
    @DisplayName("Deve registrar SAIDA ao adicionar PECA em OS aprovada")
    void deveRegistrarSaidaAoAdicionarPecaEmOsAprovada() {
        os.setDataAprovacao(LocalDateTime.now());
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.adicionarItem(1L, new ItemNovo(1L, 3, null, null));

        verify(estoqueGateway).registrarMovimentacao(eq(1L), eq(TipoMovimentacao.SAIDA), eq(3), any(), eq(1L));
        verify(estoqueGateway).sincronizarSaldo(1L);
    }

    @Test
    @DisplayName("Deve adicionar SERVICO em OS aprovada sem afetar estoque")
    void deveAdicionarServicoSemEstoque() {
        os.setDataAprovacao(LocalDateTime.now());
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(produtoGateway.buscarPorId(2L)).thenReturn(Optional.of(servico));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.adicionarItem(1L, new ItemNovo(2L, 1, null, null));

        verify(estoqueGateway, never()).registrarMovimentacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao adicionar item em OS finalizada")
    void deveLancarExcecaoAdicionarItemFinalizada() {
        os.setStatus(StatusOS.FINALIZADA);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(produtoGateway.buscarPorId(1L)).thenReturn(Optional.of(peca));

        assertThatThrownBy(() -> useCase.adicionarItem(1L, new ItemNovo(1L, 1, null, null)))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("finalizada");
    }

    @Test
    @DisplayName("Deve listar apenas OS ativas, ordenadas por status e mais antigas primeiro")
    void deveListarAtivasOrdenadas() {
        LocalDateTime agora = LocalDateTime.now();
        OrdemServico recebidaAntiga = base("OS1", StatusOS.RECEBIDA, agora.minusHours(3));
        OrdemServico recebidaNova = base("OS2", StatusOS.RECEBIDA, agora.minusHours(1));
        OrdemServico emDiagnostico = base("OS3", StatusOS.EM_DIAGNOSTICO, agora.minusHours(2));
        OrdemServico aguardando = base("OS4", StatusOS.AGUARDANDO_APROVACAO, agora.minusHours(4));
        OrdemServico emExecucao = base("OS5", StatusOS.EM_EXECUCAO, agora.minusHours(5));
        when(osGateway.listarAtivas())
                .thenReturn(List.of(recebidaAntiga, recebidaNova, emDiagnostico, aguardando, emExecucao));

        List<OrdemServico> lista = useCase.listarTodas();

        assertThat(lista).extracting(OrdemServico::getNumero).containsExactly("OS5", "OS4", "OS3", "OS1", "OS2");
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há OS ativas")
    void deveRetornarListaVazia() {
        when(osGateway.listarAtivas()).thenReturn(List.of());
        assertThat(useCase.listarTodas()).isEmpty();
    }

    @Test
    @DisplayName("Deve listar OS por status")
    void deveListarPorStatus() {
        when(osGateway.listarPorStatus(StatusOS.RECEBIDA)).thenReturn(List.of(os));
        assertThat(useCase.listarPorStatus(StatusOS.RECEBIDA)).hasSize(1);
    }

    @Test
    @DisplayName("Deve remover item de OS não aprovada sem afetar estoque")
    void deveRemoverItemNaoAprovada() {
        os.getItens().add(OsItem.builder().id(10L).produtoId(1L).produtoTipo(TipoProduto.PECA)
                .quantidade(2).precoUnitario(new BigDecimal("50.00")).build());
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.removerItem(1L, 10L);

        assertThat(os.getItens()).isEmpty();
        verify(estoqueGateway, never()).registrarMovimentacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve estornar estoque ao remover PECA de OS aprovada")
    void deveEstornarAoRemoverPecaAprovada() {
        os.setDataAprovacao(LocalDateTime.now());
        os.getItens().add(OsItem.builder().id(10L).produtoId(1L).produtoTipo(TipoProduto.PECA)
                .quantidade(3).precoUnitario(new BigDecimal("50.00")).build());
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.removerItem(1L, 10L);

        verify(estoqueGateway).registrarMovimentacao(eq(1L), eq(TipoMovimentacao.ENTRADA), eq(3), any(), eq(1L));
        verify(estoqueGateway).sincronizarSaldo(1L);
    }

    @Test
    @DisplayName("Deve remover SERVICO de OS aprovada sem afetar estoque")
    void deveRemoverServicoAprovadaSemEstoque() {
        os.setDataAprovacao(LocalDateTime.now());
        os.getItens().add(OsItem.builder().id(11L).produtoId(2L).produtoTipo(TipoProduto.SERVICO)
                .quantidade(1).precoUnitario(new BigDecimal("80.00")).build());
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.removerItem(1L, 11L);

        verify(estoqueGateway, never()).registrarMovimentacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao remover item que não pertence à OS")
    void deveLancarExcecaoRemoverItemInexistente() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        assertThatThrownBy(() -> useCase.removerItem(1L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private OrdemServico base(String numero, StatusOS status, LocalDateTime criadoEm) {
        return OrdemServico.builder().numero(numero).cliente(cliente).veiculo(veiculo)
                .status(status).valorTotal(BigDecimal.ZERO).itens(new ArrayList<>()).criadoEm(criadoEm).build();
    }

    @Test
    @DisplayName("Criar OS deve registrar a métrica de criação")
    void criarDeveRegistrarMetrica() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(cliente));
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculo));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.criar(1L, 1L, "Motor falhando", null, List.of());

        verify(metricasGateway).ordemServicoCriada();
    }

    @Test
    @DisplayName("Avançar status deve registrar a transição com o tempo no status anterior")
    void avancarStatusDeveRegistrarTransicao() {
        os.setStatusAlteradoEm(LocalDateTime.now().minusHours(2));
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.avancarStatus(1L);

        verify(metricasGateway).transicaoDeStatus(
                eq(StatusOS.RECEBIDA), eq(StatusOS.EM_DIAGNOSTICO), duracaoCaptor.capture());
        assertThat(duracaoCaptor.getValue().toHours()).isEqualTo(2);
    }

    @Test
    @DisplayName("Aprovar deve registrar a transição para EM_EXECUCAO")
    void aprovarDeveRegistrarTransicao() {
        os.setStatus(StatusOS.AGUARDANDO_APROVACAO);
        os.setStatusAlteradoEm(LocalDateTime.now().minusHours(5));
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.aprovar(1L, true);

        verify(metricasGateway).transicaoDeStatus(
                eq(StatusOS.AGUARDANDO_APROVACAO), eq(StatusOS.EM_EXECUCAO), duracaoCaptor.capture());
        assertThat(duracaoCaptor.getValue().toHours()).isEqualTo(5);
    }

    @Test
    @DisplayName("Deve registrar falha de integração quando a notificação de aprovação falha")
    void deveRegistrarFalhaDeIntegracaoNaNotificacao() {
        os.setStatus(StatusOS.EM_DIAGNOSTICO);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGateway.gerarToken(1L, true))
                .thenThrow(new IllegalStateException("chave de assinatura indisponível"));

        useCase.avancarStatus(1L);

        verify(metricasGateway).falhaDeIntegracao("notificacao-aprovacao", "IllegalStateException");
    }

    @Test
    @DisplayName("Falha na notificação não deve impedir a transição de status")
    void falhaNaNotificacaoNaoDeveImpedirTransicao() {
        os.setStatus(StatusOS.EM_DIAGNOSTICO);
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));
        when(osGateway.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenGateway.gerarToken(1L, true))
                .thenThrow(new IllegalStateException("chave de assinatura indisponível"));

        OrdemServico resultado = useCase.avancarStatus(1L);

        assertThat(resultado.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
    }

    @Test
    @DisplayName("Deve devolver a OS quando ela pertence ao cliente autenticado")
    void buscarDoClienteDevolveOsDoProprioCliente() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));

        assertThat(useCase.buscarDoCliente(1L, 1L)).isSameAs(os);
    }

    @Test
    @DisplayName("Deve responder como inexistente a OS de outro cliente")
    void buscarDoClienteNaoRevelaOsDeOutroCliente() {
        when(osGateway.buscarPorId(1L)).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> useCase.buscarDoCliente(1L, 2L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando a OS não existe")
    void buscarDoClienteOsInexistente() {
        when(osGateway.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.buscarDoCliente(99L, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
