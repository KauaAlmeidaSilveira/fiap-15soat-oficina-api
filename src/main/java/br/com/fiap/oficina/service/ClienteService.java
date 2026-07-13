package br.com.fiap.oficina.service;

import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.response.ClienteResponse;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    @Transactional
    public ClienteResponse criar(ClienteRequest request) {
        if (clienteRepository.existsByCpfCnpj(request.cpfCnpj())) {
            throw new RegraDeNegocioException("Já existe um cliente com o CPF/CNPJ: " + request.cpfCnpj());
        }
        Cliente cliente = Cliente.builder()
                .nome(request.nome())
                .cpfCnpj(request.cpfCnpj())
                .tipoDocumento(request.tipoDocumento())
                .telefone(request.telefone())
                .email(request.email())
                .endereco(request.endereco())
                .build();
        return toResponse(clienteRepository.save(cliente));
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(Long id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscarPorCpfCnpj(String cpfCnpj) {
        return toResponse(clienteRepository.findByCpfCnpj(cpfCnpj)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado com CPF/CNPJ: " + cpfCnpj)));
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listarTodos() {
        return clienteRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest request) {
        Cliente cliente = findById(id);
        if (!cliente.getCpfCnpj().equals(request.cpfCnpj()) &&
            clienteRepository.existsByCpfCnpj(request.cpfCnpj())) {
            throw new RegraDeNegocioException("CPF/CNPJ já cadastrado para outro cliente.");
        }
        cliente.setNome(request.nome());
        cliente.setCpfCnpj(request.cpfCnpj());
        cliente.setTipoDocumento(request.tipoDocumento());
        cliente.setTelefone(request.telefone());
        cliente.setEmail(request.email());
        cliente.setEndereco(request.endereco());
        return toResponse(clienteRepository.save(cliente));
    }

    @Transactional
    public void deletar(Long id) {
        findById(id);
        clienteRepository.deleteById(id);
    }

    private Cliente findById(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }

    public ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(), c.getCpfCnpj(),
                c.getTipoDocumento(), c.getTelefone(), c.getEmail(),
                c.getEndereco(), c.getCriadoEm());
    }
}
