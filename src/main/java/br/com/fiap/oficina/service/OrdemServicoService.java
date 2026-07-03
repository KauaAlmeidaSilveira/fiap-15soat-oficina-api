package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.enums.StatusOS;
import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.model.MovimentacaoEstoque;
import br.com.fiap.oficina.domain.model.OrdemServico;
import br.com.fiap.oficina.domain.model.OsItem;
import br.com.fiap.oficina.domain.model.Produto;
import br.com.fiap.oficina.domain.model.Veiculo;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.domain.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.domain.repository.OrdemServicoRepository;
import br.com.fiap.oficina.domain.repository.OsItemRepository;
import br.com.fiap.oficina.domain.repository.ProdutoRepository;
import br.com.fiap.oficina.domain.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.AprovarOsRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.ClienteResponse;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.dto.response.VeiculoResponse;
import br.com.fiap.oficina.handler.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrdemServicoService {

    private static final List<StatusOS> STATUS_EXCLUIDOS_LISTAGEM = List.of(
            StatusOS.FINALIZADA, StatusOS.ENTREGUE, StatusOS.REPROVADA);

    private static final Map<StatusOS, Integer> PRIORIDADE_LISTAGEM = Map.of(
            StatusOS.EM_EXECUCAO, 1,
            StatusOS.AGUARDANDO_APROVACAO, 2,
            StatusOS.EM_DIAGNOSTICO, 3,
            StatusOS.RECEBIDA, 4);

    private final EntityManager entityManager;

    private final OrdemServicoRepository osRepository;
    private final OsItemRepository osItemRepository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final ProdutoService produtoService;
    private final ClienteService clienteService;
    private final VeiculoService veiculoService;

    @Transactional
    public OrdemServicoResponse criar(OrdemServicoRequest request) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", request.clienteId()));
        Veiculo veiculo = veiculoRepository.findById(request.veiculoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", request.veiculoId()));

        OrdemServico os = OrdemServico.builder()
                .cliente(cliente)
                .veiculo(veiculo)
                .numero("OS" + System.currentTimeMillis())
                .status(StatusOS.RECEBIDA)
                .descricaoProblema(request.descricaoProblema())
                .observacoes(request.observacoes())
                .build();

        if (request.itens() != null && !request.itens().isEmpty()) {
            List<OsItem> itens = new ArrayList<>();
            for (OrdemServicoRequest.OsItemRequest itemReq : request.itens()) {
                Produto produto = produtoRepository.findById(itemReq.produtoId())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", itemReq.produtoId()));
                OsItem item = OsItem.builder()
                        .ordemServico(os)
                        .produto(produto)
                        .quantidade(itemReq.quantidade())
                        .precoUnitario(itemReq.precoUnitario() != null
                                ? itemReq.precoUnitario()
                                : produto.getPrecoUnitario())
                        .observacao(itemReq.observacao())
                        .build();
                itens.add(item);
            }
            os.setItens(itens);
            os.setValorTotal(calcularTotal(itens));
        }

        return toResponse(osRepository.save(os));
    }

    @Transactional(readOnly = true)
    public OrdemServicoResponse buscarPorId(Long id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public OrdemServicoResponse buscarPorNumero(String numero) {
        return toResponse(osRepository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OS não encontrada com número: " + numero)));
    }

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listarTodas() {
        return osRepository.findByStatusNotIn(STATUS_EXCLUIDOS_LISTAGEM).stream()
                .sorted(Comparator
                        .comparing((OrdemServico os) -> PRIORIDADE_LISTAGEM.get(os.getStatus()))
                        .thenComparing(OrdemServico::getCriadoEm))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listarPorCliente(Long clienteId) {
        return osRepository.findByClienteId(clienteId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrdemServicoResponse> listarPorStatus(StatusOS status) {
        return osRepository.findByStatus(status).stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrdemServicoResponse avancarStatus(Long id) {
        OrdemServico os = findById(id);
        StatusOS statusAnterior = os.getStatus();
        StatusOS novoStatus = switch (os.getStatus()) {
            case RECEBIDA -> StatusOS.EM_DIAGNOSTICO;
            case EM_DIAGNOSTICO -> StatusOS.AGUARDANDO_APROVACAO;
            case AGUARDANDO_APROVACAO -> StatusOS.EM_EXECUCAO;
            case EM_EXECUCAO -> StatusOS.FINALIZADA;
            case FINALIZADA -> StatusOS.ENTREGUE;
            case ENTREGUE -> throw new RegraDeNegocioException("OS já foi entregue ao cliente.");
            case REPROVADA -> throw new RegraDeNegocioException("OS reprovada não pode ter o status avançado.");
        };
        os.setStatus(novoStatus);

        switch (novoStatus) {
            case EM_EXECUCAO -> os.setDataInicio(LocalDateTime.now());
            case FINALIZADA -> os.setDataFim(LocalDateTime.now());
            case ENTREGUE -> os.setDataEntrega(LocalDateTime.now());
            default -> { }
        }

        if (statusAnterior == StatusOS.AGUARDANDO_APROVACAO) {
            os.setDataAprovacao(LocalDateTime.now());
            registrarSaidasEstoque(os);
        }

        return toResponse(osRepository.save(os));
    }

    @Transactional
    public OrdemServicoResponse aprovar(Long id, AprovarOsRequest request) {
        OrdemServico os = findById(id);
        if (os.getStatus() != StatusOS.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException("A OS precisa estar com status AGUARDANDO_APROVACAO para ser aprovada ou reprovada.");
        }
        if (request.aprovado()) {
            os.setDataAprovacao(LocalDateTime.now());
            os.setStatus(StatusOS.EM_EXECUCAO);
            os.setDataInicio(LocalDateTime.now());
            registrarSaidasEstoque(os);
        } else {
            os.setStatus(StatusOS.REPROVADA);
        }
        return toResponse(osRepository.save(os));
    }

    private void registrarSaidasEstoque(OrdemServico os) {
        for (OsItem item : os.getItens()) {
            Produto produto = item.getProduto();
            if (TipoProduto.PECA.equals(produto.getTipo())) {
                MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                        .produto(produto)
                        .tipo(TipoMovimentacao.SAIDA)
                        .quantidade(item.getQuantidade())
                        .motivo("Saída automática - aprovação da OS " + os.getNumero())
                        .ordemServico(os)
                        .build();
                movimentacaoEstoqueRepository.save(mov);
                produtoService.sincronizarSaldo(produto.getId());
            }
        }
    }

    @Transactional
    public OrdemServicoResponse adicionarItem(Long osId, OrdemServicoRequest.OsItemRequest itemReq) {
        OrdemServico os = findById(osId);
        if (os.getStatus() == StatusOS.FINALIZADA ||
            os.getStatus() == StatusOS.ENTREGUE ||
            os.getStatus() == StatusOS.REPROVADA) {
            throw new RegraDeNegocioException("Não é possível adicionar itens a uma OS finalizada, entregue ou reprovada.");
        }
        Produto produto = produtoRepository.findById(itemReq.produtoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", itemReq.produtoId()));
        OsItem item = OsItem.builder()
                .ordemServico(os)
                .produto(produto)
                .quantidade(itemReq.quantidade())
                .precoUnitario(itemReq.precoUnitario() != null ? itemReq.precoUnitario() : produto.getPrecoUnitario())
                .observacao(itemReq.observacao())
                .build();
        entityManager.persist(item);
        os.getItens().add(item);
        os.setValorTotal(calcularTotal(os.getItens()));

        if (os.getDataAprovacao() != null && TipoProduto.PECA.equals(produto.getTipo())) {
            MovimentacaoEstoque saida = MovimentacaoEstoque.builder()
                    .produto(produto)
                    .tipo(TipoMovimentacao.SAIDA)
                    .quantidade(item.getQuantidade())
                    .motivo("Saída automática - adição de item pós-aprovação da OS " + os.getNumero())
                    .ordemServico(os)
                    .build();
            movimentacaoEstoqueRepository.save(saida);
            produtoService.sincronizarSaldo(produto.getId());
        }

        return toResponse(osRepository.save(os));
    }

    @Transactional
    public OrdemServicoResponse removerItem(Long osId, Long itemId) {
        OrdemServico os = findById(osId);
        OsItem item = osItemRepository.findByIdAndOrdemServicoId(itemId, osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado na OS informada"));

        if (os.getDataAprovacao() != null && TipoProduto.PECA.equals(item.getProduto().getTipo())) {
            MovimentacaoEstoque estorno = MovimentacaoEstoque.builder()
                    .produto(item.getProduto())
                    .tipo(TipoMovimentacao.ENTRADA)
                    .quantidade(item.getQuantidade())
                    .motivo("Estorno por remoção de item da OS " + os.getNumero())
                    .ordemServico(os)
                    .build();
            movimentacaoEstoqueRepository.save(estorno);
            produtoService.sincronizarSaldo(item.getProduto().getId());
        }

        os.getItens().remove(item);
        os.setValorTotal(calcularTotal(os.getItens()));
        return toResponse(osRepository.save(os));
    }

    private BigDecimal calcularTotal(List<OsItem> itens) {
        return itens.stream()
                .map(i -> i.getQuantidade() != null && i.getPrecoUnitario() != null
                        ? BigDecimal.valueOf(i.getQuantidade()).multiply(i.getPrecoUnitario())
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public Double tempoMedioExecucao() {
        List<OrdemServico> finalizadas = osRepository.findComTempoDeExecucao();
        if (finalizadas.isEmpty()) return null;
        double somaHoras = finalizadas.stream()
                .mapToDouble(os -> Duration.between(os.getDataInicio(), os.getDataFim()).getSeconds() / 3600.0)
                .sum();
        return somaHoras / finalizadas.size();
    }

    private OrdemServico findById(Long id) {
        return osRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", id));
    }

    public OrdemServicoResponse toResponse(OrdemServico os) {
        ClienteResponse clienteResponse = clienteService.toResponse(os.getCliente());
        VeiculoResponse veiculoResponse = veiculoService.toResponse(os.getVeiculo());

        List<OrdemServicoResponse.OsItemResponse> itensResponse = os.getItens().stream()
                .map(item -> new OrdemServicoResponse.OsItemResponse(
                        item.getId(),
                        item.getProduto().getId(),
                        item.getProduto().getNome(),
                        item.getProduto().getTipo().name(),
                        item.getQuantidade(),
                        item.getPrecoUnitario(),
                        item.getQuantidade() != null && item.getPrecoUnitario() != null
                                ? BigDecimal.valueOf(item.getQuantidade()).multiply(item.getPrecoUnitario())
                                : BigDecimal.ZERO,
                        item.getObservacao()
                )).toList();

        return new OrdemServicoResponse(
                os.getId(), os.getNumero(), clienteResponse, veiculoResponse,
                os.getStatus(), os.getDescricaoProblema(), os.getObservacoes(),
                os.getValorTotal(), os.getDataInicio(), os.getDataFim(),
                os.getDataEntrega(), os.getDataAprovacao(), os.getCriadoEm(), itensResponse
        );
    }
}
