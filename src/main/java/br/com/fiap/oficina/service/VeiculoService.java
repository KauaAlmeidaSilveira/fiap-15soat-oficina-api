package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.model.ClienteVeiculo;
import br.com.fiap.oficina.domain.model.Veiculo;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.domain.repository.ClienteVeiculoRepository;
import br.com.fiap.oficina.domain.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import br.com.fiap.oficina.dto.response.VeiculoResponse;
import br.com.fiap.oficina.handler.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteVeiculoRepository clienteVeiculoRepository;

    @Transactional
    public VeiculoResponse criar(VeiculoRequest request) {
        String placa = request.placa().toUpperCase();
        if (veiculoRepository.existsByPlaca(placa)) {
            throw new RegraDeNegocioException("Veículo com placa " + placa + " já cadastrado.");
        }
        Veiculo veiculo = Veiculo.builder()
                .placa(placa)
                .marca(request.marca())
                .modelo(request.modelo())
                .ano(request.ano())
                .cor(request.cor())
                .chassi(request.chassi())
                .build();
        return toResponse(veiculoRepository.save(veiculo));
    }

    @Transactional(readOnly = true)
    public VeiculoResponse buscarPorId(Long id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public VeiculoResponse buscarPorPlaca(String placa) {
        return toResponse(veiculoRepository.findByPlaca(placa.toUpperCase())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado com placa: " + placa)));
    }

    @Transactional(readOnly = true)
    public List<VeiculoResponse> listarTodos() {
        return veiculoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public VeiculoResponse atualizar(Long id, VeiculoRequest request) {
        Veiculo veiculo = findById(id);
        String placa = request.placa().toUpperCase();
        if (!veiculo.getPlaca().equals(placa) && veiculoRepository.existsByPlaca(placa)) {
            throw new RegraDeNegocioException("Placa já cadastrada para outro veículo.");
        }
        veiculo.setPlaca(placa);
        veiculo.setMarca(request.marca());
        veiculo.setModelo(request.modelo());
        veiculo.setAno(request.ano());
        veiculo.setCor(request.cor());
        veiculo.setChassi(request.chassi());
        return toResponse(veiculoRepository.save(veiculo));
    }

    @Transactional
    public void deletar(Long id) {
        findById(id);
        veiculoRepository.deleteById(id);
    }

    @Transactional
    public void vincularCliente(Long veiculoId, Long clienteId) {
        Veiculo veiculo = findById(veiculoId);
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
        if (clienteVeiculoRepository.existsByClienteIdAndVeiculoId(clienteId, veiculoId)) {
            throw new RegraDeNegocioException("Cliente já vinculado a este veículo.");
        }
        ClienteVeiculo cv = ClienteVeiculo.builder()
                .cliente(cliente)
                .veiculo(veiculo)
                .build();
        clienteVeiculoRepository.save(cv);
    }

    @Transactional(readOnly = true)
    public List<VeiculoResponse> listarPorCliente(Long clienteId) {
        return clienteVeiculoRepository.findByClienteId(clienteId)
                .stream()
                .map(cv -> toResponse(cv.getVeiculo()))
                .toList();
    }

    private Veiculo findById(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", id));
    }

    public VeiculoResponse toResponse(Veiculo v) {
        return new VeiculoResponse(v.getId(), v.getPlaca(), v.getMarca(),
                v.getModelo(), v.getAno(), v.getCor(), v.getChassi(), v.getCriadoEm());
    }
}
