package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.enums.StatusOS;
import br.com.fiap.oficina.dto.request.AprovarOsRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import br.com.fiap.oficina.service.OrdemServicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ordens-servico")
@RequiredArgsConstructor
@Tag(name = "Ordens de Serviço", description = "Gestão completa de ordens de serviço")
public class OrdemServicoController {

    private final OrdemServicoService osService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    @Operation(summary = "Criar nova Ordem de Serviço")
    public ResponseEntity<OrdemServicoResponse> criar(@Valid @RequestBody OrdemServicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(osService.criar(request));
    }

    @GetMapping
    @Operation(summary = "Listar todas as OS")
    public ResponseEntity<List<OrdemServicoResponse>> listar(
            @RequestParam(required = false) StatusOS status,
            @RequestParam(required = false) Long clienteId) {
        if (status != null) return ResponseEntity.ok(osService.listarPorStatus(status));
        if (clienteId != null) return ResponseEntity.ok(osService.listarPorCliente(clienteId));
        return ResponseEntity.ok(osService.listarTodas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar OS por ID")
    public ResponseEntity<OrdemServicoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(osService.buscarPorId(id));
    }

    @GetMapping("/numero/{numero}")
    @Operation(summary = "Buscar OS por número (acesso do cliente)")
    public ResponseEntity<OrdemServicoResponse> buscarPorNumero(@PathVariable String numero) {
        return ResponseEntity.ok(osService.buscarPorNumero(numero));
    }

    @PatchMapping("/{id}/avancar-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Avançar status da OS para o próximo")
    public ResponseEntity<OrdemServicoResponse> avancarStatus(@PathVariable Long id) {
        return ResponseEntity.ok(osService.avancarStatus(id));
    }

    @PatchMapping("/{id}/aprovar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Registra a decisão do cliente sobre o orçamento (aprovado: true = aprovado, false = reprovado)")
    public ResponseEntity<OrdemServicoResponse> aprovar(
            @PathVariable Long id,
            @Valid @RequestBody AprovarOsRequest request) {
        return ResponseEntity.ok(osService.aprovar(id, request));
    }

    @PostMapping("/{id}/itens")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Adicionar item à OS")
    public ResponseEntity<OrdemServicoResponse> adicionarItem(
            @PathVariable Long id,
            @Valid @RequestBody OrdemServicoRequest.OsItemRequest itemRequest) {
        return ResponseEntity.ok(osService.adicionarItem(id, itemRequest));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    @Operation(summary = "Remover item da OS. Se já aprovada, estorna o estoque da peça.")
    public ResponseEntity<OrdemServicoResponse> removerItem(
            @PathVariable Long id,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(osService.removerItem(id, itemId));
    }

    @GetMapping("/metricas/tempo-medio")
    @Operation(summary = "Tempo médio de execução das OS em horas")
    public ResponseEntity<Map<String, Object>> tempoMedio() {
        Double tempo = osService.tempoMedioExecucao();
        return ResponseEntity.ok(Map.of("tempoMedioHoras", tempo != null ? tempo : 0.0));
    }
}
