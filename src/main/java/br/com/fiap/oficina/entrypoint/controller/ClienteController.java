package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.usecase.ClienteUseCase;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.response.ClienteResponse;
import br.com.fiap.oficina.entrypoint.controller.mapper.ClienteDtoMapper;
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
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Gestão de clientes da oficina")
public class ClienteController {

    private final ClienteUseCase clienteUseCase;
    private final ClienteDtoMapper mapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Cadastrar novo cliente")
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        Cliente criado = clienteUseCase.criar(mapper.toDomain(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(criado));
    }

    @GetMapping
    @Operation(summary = "Listar todos os clientes")
    public ResponseEntity<List<ClienteResponse>> listar() {
        List<ClienteResponse> clientes = clienteUseCase.listarTodos().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(clientes);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(clienteUseCase.buscarPorId(id)));
    }

    @GetMapping("/cpf-cnpj/{cpfCnpj}")
    @Operation(summary = "Buscar cliente por CPF/CNPJ")
    public ResponseEntity<ClienteResponse> buscarPorCpfCnpj(@PathVariable String cpfCnpj) {
        return ResponseEntity.ok(mapper.toResponse(clienteUseCase.buscarPorCpfCnpj(cpfCnpj)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Atualizar cliente")
    public ResponseEntity<ClienteResponse> atualizar(@PathVariable Long id,
                                                     @Valid @RequestBody ClienteRequest request) {
        Cliente atualizado = clienteUseCase.atualizar(id, mapper.toDomain(request));
        return ResponseEntity.ok(mapper.toResponse(atualizado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Deletar cliente")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        clienteUseCase.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
