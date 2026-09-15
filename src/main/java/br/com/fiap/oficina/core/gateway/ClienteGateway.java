package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.enums.StatusCliente;

import java.util.List;
import java.util.Optional;

public interface ClienteGateway {

    Cliente salvar(Cliente cliente);

    Optional<Cliente> buscarPorId(Long id);

    Optional<Cliente> buscarPorCpfCnpj(String cpfCnpj);

    List<Cliente> listarPorStatus(StatusCliente status);

    boolean existePorCpfCnpj(String cpfCnpj);
}
