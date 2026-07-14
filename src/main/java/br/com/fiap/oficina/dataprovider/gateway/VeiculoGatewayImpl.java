package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import br.com.fiap.oficina.dataprovider.persistence.entity.ClienteVeiculo;
import br.com.fiap.oficina.dataprovider.persistence.mapper.VeiculoPersistenceMapper;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteVeiculoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VeiculoGatewayImpl implements VeiculoGateway {

    private final VeiculoRepository veiculoRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteVeiculoRepository clienteVeiculoRepository;
    private final VeiculoPersistenceMapper mapper;

    @Override
    @Transactional
    public Veiculo salvar(Veiculo veiculo) {
        if (veiculo.getId() != null) {
            var existente = veiculoRepository.findById(veiculo.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", veiculo.getId()));
            mapper.updateEntity(existente, veiculo);
            return mapper.toDomain(veiculoRepository.save(existente));
        }
        return mapper.toDomain(veiculoRepository.save(mapper.toEntity(veiculo)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Veiculo> buscarPorId(Long id) {
        return veiculoRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Veiculo> buscarPorPlaca(String placa) {
        return veiculoRepository.findByPlaca(placa).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Veiculo> listarTodos() {
        return veiculoRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deletarPorId(Long id) {
        veiculoRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorPlaca(String placa) {
        return veiculoRepository.existsByPlaca(placa);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Veiculo> listarPorClienteId(Long clienteId) {
        return clienteVeiculoRepository.findByClienteId(clienteId).stream()
                .map(cv -> mapper.toDomain(cv.getVeiculo()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeVinculo(Long clienteId, Long veiculoId) {
        return clienteVeiculoRepository.existsByClienteIdAndVeiculoId(clienteId, veiculoId);
    }

    @Override
    @Transactional
    public void vincularCliente(Long clienteId, Long veiculoId) {
        var veiculo = veiculoRepository.findById(veiculoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", veiculoId));
        var cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
        clienteVeiculoRepository.save(ClienteVeiculo.builder()
                .cliente(cliente)
                .veiculo(veiculo)
                .build());
    }
}
