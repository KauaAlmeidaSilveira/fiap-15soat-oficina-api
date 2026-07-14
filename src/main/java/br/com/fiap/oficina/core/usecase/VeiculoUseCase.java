package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;

import java.util.List;

public class VeiculoUseCase {

    private final VeiculoGateway veiculoGateway;
    private final ClienteGateway clienteGateway;

    public VeiculoUseCase(VeiculoGateway veiculoGateway, ClienteGateway clienteGateway) {
        this.veiculoGateway = veiculoGateway;
        this.clienteGateway = clienteGateway;
    }

    public Veiculo criar(Veiculo novo) {
        String placa = novo.getPlaca().toUpperCase();
        if (veiculoGateway.existePorPlaca(placa)) {
            throw new RegraDeNegocioException("Veículo com placa " + placa + " já cadastrado.");
        }
        novo.setPlaca(placa);
        return veiculoGateway.salvar(novo);
    }

    public Veiculo buscarPorId(Long id) {
        return veiculoGateway.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", id));
    }

    public Veiculo buscarPorPlaca(String placa) {
        return veiculoGateway.buscarPorPlaca(placa.toUpperCase())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado com placa: " + placa));
    }

    public List<Veiculo> listarTodos() {
        return veiculoGateway.listarTodos();
    }

    public Veiculo atualizar(Long id, Veiculo dados) {
        Veiculo existente = buscarPorId(id);
        String placa = dados.getPlaca().toUpperCase();
        if (!existente.getPlaca().equals(placa) && veiculoGateway.existePorPlaca(placa)) {
            throw new RegraDeNegocioException("Placa já cadastrada para outro veículo.");
        }
        existente.setPlaca(placa);
        existente.setMarca(dados.getMarca());
        existente.setModelo(dados.getModelo());
        existente.setAno(dados.getAno());
        existente.setCor(dados.getCor());
        existente.setChassi(dados.getChassi());
        return veiculoGateway.salvar(existente);
    }

    public void deletar(Long id) {
        buscarPorId(id);
        veiculoGateway.deletarPorId(id);
    }

    public void vincularCliente(Long veiculoId, Long clienteId) {
        buscarPorId(veiculoId);
        clienteGateway.buscarPorId(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
        if (veiculoGateway.existeVinculo(clienteId, veiculoId)) {
            throw new RegraDeNegocioException("Cliente já vinculado a este veículo.");
        }
        veiculoGateway.vincularCliente(clienteId, veiculoId);
    }

    public List<Veiculo> listarPorCliente(Long clienteId) {
        return veiculoGateway.listarPorClienteId(clienteId);
    }
}
