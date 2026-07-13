package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.dataprovider.persistence.mapper.OrdemServicoPersistenceMapper;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OrdemServicoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrdemServicoGatewayImpl implements OrdemServicoGateway {

    private static final List<StatusOS> EXCLUIDAS_LISTAGEM =
            List.of(StatusOS.FINALIZADA, StatusOS.ENTREGUE, StatusOS.REPROVADA);

    private final OrdemServicoRepository osRepository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final ProdutoRepository produtoRepository;
    private final OrdemServicoPersistenceMapper mapper;

    @Override
    @Transactional
    public OrdemServico salvar(OrdemServico os) {
        br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico entity = os.getId() != null
                ? osRepository.findById(os.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de Serviço", os.getId()))
                : novaEntidade(os);

        entity.setStatus(os.getStatus());
        entity.setDescricaoProblema(os.getDescricaoProblema());
        entity.setObservacoes(os.getObservacoes());
        entity.setValorTotal(os.getValorTotal());
        entity.setDataInicio(os.getDataInicio());
        entity.setDataFim(os.getDataFim());
        entity.setDataEntrega(os.getDataEntrega());
        entity.setDataAprovacao(os.getDataAprovacao());
        sincronizarItens(entity, os.getItens());

        return mapper.toDomain(osRepository.save(entity));
    }

    private br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico novaEntidade(OrdemServico os) {
        var entity = new br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico();
        entity.setItens(new ArrayList<>());
        entity.setNumero(os.getNumero());
        entity.setCliente(clienteRepository.getReferenceById(os.getCliente().getId()));
        entity.setVeiculo(veiculoRepository.getReferenceById(os.getVeiculo().getId()));
        return entity;
    }

    private void sincronizarItens(br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico entity,
                                  List<OsItem> itens) {
        Set<Long> mantidos = itens.stream()
                .map(OsItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        entity.getItens().removeIf(it -> it.getId() != null && !mantidos.contains(it.getId()));
        for (OsItem di : itens) {
            if (di.getId() == null) {
                entity.getItens().add(br.com.fiap.oficina.dataprovider.persistence.entity.OsItem.builder()
                        .ordemServico(entity)
                        .produto(produtoRepository.getReferenceById(di.getProdutoId()))
                        .quantidade(di.getQuantidade())
                        .precoUnitario(di.getPrecoUnitario())
                        .observacao(di.getObservacao())
                        .build());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrdemServico> buscarPorId(Long id) {
        return osRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrdemServico> buscarPorNumero(String numero) {
        return osRepository.findByNumero(numero).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdemServico> listarAtivas() {
        return osRepository.findByStatusNotIn(EXCLUIDAS_LISTAGEM).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdemServico> listarPorClienteId(Long clienteId) {
        return osRepository.findByClienteId(clienteId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdemServico> listarPorStatus(StatusOS status) {
        return osRepository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdemServico> listarComTempoDeExecucao() {
        return osRepository.findComTempoDeExecucao().stream().map(mapper::toDomain).toList();
    }
}
