package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class OrdemServicoUseCase {

    private static final System.Logger LOG = System.getLogger(OrdemServicoUseCase.class.getName());

    private static final Map<StatusOS, Integer> PRIORIDADE_LISTAGEM = Map.of(
            StatusOS.EM_EXECUCAO, 1,
            StatusOS.AGUARDANDO_APROVACAO, 2,
            StatusOS.EM_DIAGNOSTICO, 3,
            StatusOS.RECEBIDA, 4);

    private final OrdemServicoGateway osGateway;
    private final ClienteGateway clienteGateway;
    private final VeiculoGateway veiculoGateway;
    private final ProdutoGateway produtoGateway;
    private final EstoqueGateway estoqueGateway;
    private final TokenAprovacaoGateway tokenGateway;
    private final NotificacaoAprovacaoGateway notificacaoGateway;
    private final MetricasGateway metricasGateway;
    private final String publicBaseUrl;

    public OrdemServicoUseCase(OrdemServicoGateway osGateway, ClienteGateway clienteGateway,
                               VeiculoGateway veiculoGateway, ProdutoGateway produtoGateway,
                               EstoqueGateway estoqueGateway, TokenAprovacaoGateway tokenGateway,
                               NotificacaoAprovacaoGateway notificacaoGateway,
                               MetricasGateway metricasGateway, String publicBaseUrl) {
        this.osGateway = osGateway;
        this.clienteGateway = clienteGateway;
        this.veiculoGateway = veiculoGateway;
        this.produtoGateway = produtoGateway;
        this.estoqueGateway = estoqueGateway;
        this.tokenGateway = tokenGateway;
        this.notificacaoGateway = notificacaoGateway;
        this.metricasGateway = metricasGateway;
        this.publicBaseUrl = publicBaseUrl;
    }

    public record ItemNovo(Long produtoId, Integer quantidade, BigDecimal precoUnitario, String observacao) {}

    public OrdemServico criar(Long clienteId, Long veiculoId, String descricaoProblema,
                              String observacoes, List<ItemNovo> itens) {
        LOG.log(System.Logger.Level.INFO,
                "Recebida solicitação de abertura de OS para o cliente " + clienteId + " e veículo " + veiculoId);
        Cliente cliente = clienteGateway.buscarPorId(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
        if (!cliente.estaAtivo()) {
            throw new RegraDeNegocioException("Não é possível abrir ordem de serviço para cliente inativo.");
        }
        Veiculo veiculo = veiculoGateway.buscarPorId(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", veiculoId));

        OrdemServico os = OrdemServico.builder()
                .numero("OS" + System.currentTimeMillis())
                .cliente(cliente)
                .veiculo(veiculo)
                .status(StatusOS.RECEBIDA)
                .descricaoProblema(descricaoProblema)
                .observacoes(observacoes)
                .itens(new ArrayList<>())
                .build();

        if (itens != null) {
            for (ItemNovo in : itens) {
                os.getItens().add(construirItem(in));
            }
            os.recalcularTotal();
        }
        LOG.log(System.Logger.Level.INFO,
                "Processando abertura da OS " + os.getNumero() + " com " + os.getItens().size() + " item(ns)");
        OrdemServico criada = osGateway.salvar(os);
        metricasGateway.ordemServicoCriada();
        LOG.log(System.Logger.Level.INFO,
                "OS " + criada.getNumero() + " criada com sucesso, status " + criada.getStatus());
        return criada;
    }

    public OrdemServico buscarPorId(Long id) {
        return osGateway.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", id));
    }

    public OrdemServico buscarPorNumero(String numero) {
        return osGateway.buscarPorNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada com número: " + numero));
    }

    public OrdemServico buscarDoCliente(Long osId, Long clienteId) {
        return osGateway.buscarPorId(osId)
                .filter(os -> os.getCliente() != null && clienteId.equals(os.getCliente().getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", osId));
    }

    public List<OrdemServico> listarTodas() {
        return osGateway.listarAtivas().stream()
                .sorted(Comparator
                        .comparing((OrdemServico os) -> PRIORIDADE_LISTAGEM.get(os.getStatus()))
                        .thenComparing(OrdemServico::getCriadoEm))
                .toList();
    }

    public List<OrdemServico> listarPorCliente(Long clienteId) {
        return osGateway.listarPorClienteId(clienteId);
    }

    public List<OrdemServico> listarPorStatus(StatusOS status) {
        return osGateway.listarPorStatus(status);
    }

    public OrdemServico avancarStatus(Long id) {
        OrdemServico os = buscarPorId(id);
        StatusOS anterior = os.getStatus();
        LOG.log(System.Logger.Level.INFO,
                "Recebida solicitação para avançar status da OS " + os.getNumero() + " (status atual: " + anterior + ")");
        Duration tempoNoStatusAnterior = os.tempoNoStatusAtual();
        os.avancarStatus();
        StatusOS novo = os.getStatus();

        if (anterior == StatusOS.AGUARDANDO_APROVACAO) {
            registrarSaidasEstoque(os);
        }
        OrdemServico salva = osGateway.salvar(os);

        if (novo == StatusOS.AGUARDANDO_APROVACAO) {
            notificarAprovacaoPendente(salva);
        }
        metricasGateway.transicaoDeStatus(anterior, novo, tempoNoStatusAnterior);
        LOG.log(System.Logger.Level.INFO,
                "OS " + salva.getNumero() + " avançou de " + anterior + " para " + novo);
        if (novo == StatusOS.ENTREGUE) {
            LOG.log(System.Logger.Level.INFO,
                    "Ciclo de vida da OS " + salva.getNumero() + " concluído: entregue ao cliente");
        }
        return salva;
    }

    public OrdemServico aprovar(Long id, boolean aprovado) {
        OrdemServico os = buscarPorId(id);
        StatusOS anterior = os.getStatus();
        LOG.log(System.Logger.Level.INFO,
                "Recebida decisão de aprovação da OS " + os.getNumero() + ": aprovado=" + aprovado);
        Duration tempoNoStatusAnterior = os.tempoNoStatusAtual();
        os.aprovar(aprovado);
        if (aprovado) {
            registrarSaidasEstoque(os);
        }
        OrdemServico salva = osGateway.salvar(os);
        metricasGateway.transicaoDeStatus(anterior, salva.getStatus(), tempoNoStatusAnterior);
        LOG.log(System.Logger.Level.INFO,
                "OS " + salva.getNumero() + " teve a aprovação processada: " + anterior + " -> " + salva.getStatus());
        return salva;
    }

    public boolean processarDecisaoViaToken(String token) {
        var decodificado = tokenGateway.validar(token);
        LOG.log(System.Logger.Level.INFO,
                "Processando decisão de aprovação via link de e-mail para a OS " + decodificado.osId());
        aprovar(decodificado.osId(), decodificado.aprovado());
        return decodificado.aprovado();
    }

    public OrdemServico adicionarItem(Long osId, ItemNovo itemNovo) {
        LOG.log(System.Logger.Level.INFO, "Recebida solicitação para adicionar item à OS " + osId);
        OrdemServico os = buscarPorId(osId);
        OsItem item = construirItem(itemNovo);
        os.adicionarItem(item);
        OrdemServico salva = osGateway.salvar(os);

        if (os.isAprovada() && item.getProdutoTipo() == TipoProduto.PECA) {
            estoqueGateway.registrarMovimentacao(item.getProdutoId(), TipoMovimentacao.SAIDA, item.getQuantidade(),
                    "Saída automática - adição de item pós-aprovação da OS " + os.getNumero(), os.getId());
            estoqueGateway.sincronizarSaldo(item.getProdutoId());
        }
        LOG.log(System.Logger.Level.INFO,
                "Item adicionado à OS " + salva.getNumero() + ": produto " + item.getProdutoNome());
        return salva;
    }

    public OrdemServico removerItem(Long osId, Long itemId) {
        LOG.log(System.Logger.Level.INFO, "Recebida solicitação para remover item " + itemId + " da OS " + osId);
        OrdemServico os = buscarPorId(osId);
        OsItem item = os.getItens().stream()
                .filter(i -> itemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado na OS informada"));

        if (os.isAprovada() && item.getProdutoTipo() == TipoProduto.PECA) {
            estoqueGateway.registrarMovimentacao(item.getProdutoId(), TipoMovimentacao.ENTRADA, item.getQuantidade(),
                    "Estorno por remoção de item da OS " + os.getNumero(), os.getId());
            estoqueGateway.sincronizarSaldo(item.getProdutoId());
        }
        os.removerItem(item);
        OrdemServico salva = osGateway.salvar(os);
        LOG.log(System.Logger.Level.INFO,
                "Item removido da OS " + salva.getNumero() + ": produto " + item.getProdutoNome());
        return salva;
    }

    public Double tempoMedioExecucao() {
        List<OrdemServico> finalizadas = osGateway.listarComTempoDeExecucao();
        if (finalizadas.isEmpty()) {
            return null;
        }
        double somaHoras = finalizadas.stream()
                .mapToDouble(os -> Duration.between(os.getDataInicio(), os.getDataFim()).getSeconds() / 3600.0)
                .sum();
        return somaHoras / finalizadas.size();
    }

    private OsItem construirItem(ItemNovo in) {
        Produto produto = produtoGateway.buscarPorId(in.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", in.produtoId()));
        return OsItem.builder()
                .produtoId(produto.getId())
                .produtoNome(produto.getNome())
                .produtoTipo(produto.getTipo())
                .quantidade(in.quantidade())
                .precoUnitario(in.precoUnitario() != null ? in.precoUnitario() : produto.getPrecoUnitario())
                .observacao(in.observacao())
                .build();
    }

    private void registrarSaidasEstoque(OrdemServico os) {
        for (OsItem item : os.getItens()) {
            if (item.getProdutoTipo() == TipoProduto.PECA) {
                estoqueGateway.registrarMovimentacao(item.getProdutoId(), TipoMovimentacao.SAIDA, item.getQuantidade(),
                        "Saída automática - aprovação da OS " + os.getNumero(), os.getId());
                estoqueGateway.sincronizarSaldo(item.getProdutoId());
                LOG.log(System.Logger.Level.INFO,
                        "Baixa de estoque processada na OS " + os.getNumero() + ": produto " + item.getProdutoNome()
                                + ", quantidade " + item.getQuantidade());
            }
        }
    }

    private void notificarAprovacaoPendente(OrdemServico os) {
        try {
            String tokenAprovar = tokenGateway.gerarToken(os.getId(), true);
            String tokenRecusar = tokenGateway.gerarToken(os.getId(), false);
            String linkAprovar = publicBaseUrl + "/aprovacao-os?token=" + tokenAprovar;
            String linkRecusar = publicBaseUrl + "/aprovacao-os?token=" + tokenRecusar;

            var dados = new NotificacaoAprovacaoGateway.Dados(
                    os.getNumero(),
                    os.getCliente().getNome(),
                    os.getCliente().getEmail(),
                    os.getVeiculo().getMarca(),
                    os.getVeiculo().getModelo(),
                    os.getVeiculo().getPlaca(),
                    os.getItens().stream()
                            .map(item -> new NotificacaoAprovacaoGateway.Dados.Item(
                                    item.getProdutoNome(), item.getQuantidade(), item.getPrecoUnitario()))
                            .toList(),
                    os.getValorTotal());

            notificacaoGateway.notificar(dados, linkAprovar, linkRecusar);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Falha ao notificar cliente sobre aprovação pendente da OS " + os.getNumero() + ": " + e.getMessage());
            metricasGateway.falhaDeIntegracao("notificacao-aprovacao", e.getClass().getSimpleName());
        }
    }
}
