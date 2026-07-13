package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Veiculo;

import java.util.List;
import java.util.Optional;

public interface VeiculoGateway {

    Veiculo salvar(Veiculo veiculo);

    Optional<Veiculo> buscarPorId(Long id);

    Optional<Veiculo> buscarPorPlaca(String placa);

    List<Veiculo> listarTodos();

    void deletarPorId(Long id);

    boolean existePorPlaca(String placa);

    List<Veiculo> listarPorClienteId(Long clienteId);

    boolean existeVinculo(Long clienteId, Long veiculoId);

    void vincularCliente(Long clienteId, Long veiculoId);
}
