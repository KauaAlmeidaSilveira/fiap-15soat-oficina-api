package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;

import java.util.List;

public class ClienteUseCase {

    private final ClienteGateway clienteGateway;

    public ClienteUseCase(ClienteGateway clienteGateway) {
        this.clienteGateway = clienteGateway;
    }

    public Cliente criar(Cliente novo) {
        if (clienteGateway.existePorCpfCnpj(novo.getCpfCnpj())) {
            throw new RegraDeNegocioException("Já existe um cliente com o CPF/CNPJ: " + novo.getCpfCnpj());
        }
        return clienteGateway.salvar(novo);
    }

    public Cliente buscarPorId(Long id) {
        return clienteGateway.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }

    public Cliente buscarPorCpfCnpj(String cpfCnpj) {
        return clienteGateway.buscarPorCpfCnpj(cpfCnpj)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado com CPF/CNPJ: " + cpfCnpj));
    }

    public List<Cliente> listarTodos() {
        return clienteGateway.listarTodos();
    }

    public Cliente atualizar(Long id, Cliente dados) {
        Cliente existente = buscarPorId(id);
        if (!existente.getCpfCnpj().equals(dados.getCpfCnpj())
                && clienteGateway.existePorCpfCnpj(dados.getCpfCnpj())) {
            throw new RegraDeNegocioException("CPF/CNPJ já cadastrado para outro cliente.");
        }
        existente.setNome(dados.getNome());
        existente.setCpfCnpj(dados.getCpfCnpj());
        existente.setTipoDocumento(dados.getTipoDocumento());
        existente.setTelefone(dados.getTelefone());
        existente.setEmail(dados.getEmail());
        existente.setEndereco(dados.getEndereco());
        return clienteGateway.salvar(existente);
    }

    public void deletar(Long id) {
        buscarPorId(id);
        clienteGateway.deletarPorId(id);
    }
}
