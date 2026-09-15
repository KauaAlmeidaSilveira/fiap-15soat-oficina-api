package br.com.fiap.oficina.entrypoint.controller;

import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.entrypoint.controller.mapper.OrdemServicoDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/minhas-ordens-servico")
@RequiredArgsConstructor
@Tag(name = "Minhas Ordens de Serviço", description = "Consulta das ordens de serviço do cliente autenticado")
public class MinhasOrdensServicoController {

    private final OrdemServicoUseCase ordemServicoUseCase;
    private final OrdemServicoDtoMapper mapper;

    @GetMapping
    @Operation(summary = "Listar as ordens de serviço do cliente autenticado")
    public ResponseEntity<List<OrdemServicoResponse>> listar(@AuthenticationPrincipal Jwt jwt) {
        List<OrdemServicoResponse> ordens = ordemServicoUseCase.listarPorCliente(clienteId(jwt)).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(ordens);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar uma ordem de serviço do cliente autenticado")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mapper.toResponse(ordemServicoUseCase.buscarDoCliente(id, clienteId(jwt))));
    }

    private Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
