package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.dataprovider.persistence.mapper.ClientePersistenceMapper;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClienteGatewayImpl implements ClienteGateway {

    private final ClienteRepository clienteRepository;
    private final ClientePersistenceMapper mapper;

    @Override
    @Transactional
    public Cliente salvar(Cliente cliente) {
        if (cliente.getId() != null) {
            var existente = clienteRepository.findById(cliente.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", cliente.getId()));
            mapper.updateEntity(existente, cliente);
            return mapper.toDomain(clienteRepository.save(existente));
        }
        return mapper.toDomain(clienteRepository.save(mapper.toEntity(cliente)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cliente> buscarPorId(Long id) {
        return clienteRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cliente> buscarPorCpfCnpj(String cpfCnpj) {
        return clienteRepository.findByCpfCnpj(cpfCnpj).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deletarPorId(Long id) {
        clienteRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorCpfCnpj(String cpfCnpj) {
        return clienteRepository.existsByCpfCnpj(cpfCnpj);
    }
}
