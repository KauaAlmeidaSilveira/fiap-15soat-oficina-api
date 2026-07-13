package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.usecase.VeiculoUseCase;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import br.com.fiap.oficina.dto.response.VeiculoResponse;
import br.com.fiap.oficina.entrypoint.controller.mapper.VeiculoDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/veiculos")
@RequiredArgsConstructor
@Tag(name = "Veículos", description = "Gestão de veículos")
public class VeiculoController {

    private final VeiculoUseCase veiculoUseCase;
    private final VeiculoDtoMapper mapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Cadastrar veículo")
    public ResponseEntity<VeiculoResponse> criar(@Valid @RequestBody VeiculoRequest request) {
        Veiculo criado = veiculoUseCase.criar(mapper.toDomain(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(criado));
    }

    @GetMapping
    @Operation(summary = "Listar todos os veículos")
    public ResponseEntity<List<VeiculoResponse>> listar() {
        List<VeiculoResponse> veiculos = veiculoUseCase.listarTodos().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(veiculos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar veículo por ID")
    public ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(veiculoUseCase.buscarPorId(id)));
    }

    @GetMapping("/placa/{placa}")
    @Operation(summary = "Buscar veículo por placa")
    public ResponseEntity<VeiculoResponse> buscarPorPlaca(@PathVariable String placa) {
        return ResponseEntity.ok(mapper.toResponse(veiculoUseCase.buscarPorPlaca(placa)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Atualizar veículo")
    public ResponseEntity<VeiculoResponse> atualizar(@PathVariable Long id,
                                                     @Valid @RequestBody VeiculoRequest request) {
        Veiculo atualizado = veiculoUseCase.atualizar(id, mapper.toDomain(request));
        return ResponseEntity.ok(mapper.toResponse(atualizado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Deletar veículo")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        veiculoUseCase.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{veiculoId}/clientes/{clienteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Vincular cliente ao veículo")
    public ResponseEntity<Void> vincularCliente(@PathVariable Long veiculoId,
                                                @PathVariable Long clienteId) {
        veiculoUseCase.vincularCliente(veiculoId, clienteId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(summary = "Listar veículos de um cliente")
    public ResponseEntity<List<VeiculoResponse>> listarPorCliente(@PathVariable Long clienteId) {
        List<VeiculoResponse> veiculos = veiculoUseCase.listarPorCliente(clienteId).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(veiculos);
    }
}
