package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteGateway {

    Cliente salvar(Cliente cliente);

    Optional<Cliente> buscarPorId(Long id);

    Optional<Cliente> buscarPorCpfCnpj(String cpfCnpj);

    List<Cliente> listarTodos();

    void deletarPorId(Long id);

    boolean existePorCpfCnpj(String cpfCnpj);
}
