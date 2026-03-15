package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.dto.request.MovimentacaoEstoqueRequest;
import br.com.fiap.oficina.dto.request.ProdutoRequest;
import br.com.fiap.oficina.dto.response.MovimentacaoEstoqueResponse;
import br.com.fiap.oficina.dto.response.ProdutoResponse;
import br.com.fiap.oficina.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@Tag(name = "Produtos", description = "Gestão de peças e serviços")
public class ProdutoController {

    private final ProdutoService produtoService;

    @PostMapping
    @Operation(summary = "Cadastrar produto (peça ou serviço)")
    public ResponseEntity<ProdutoResponse> criar(@Valid @RequestBody ProdutoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoService.criar(request));
    }

    @GetMapping
    @Operation(summary = "Listar todos os produtos")
    public ResponseEntity<List<ProdutoResponse>> listar(
            @RequestParam(required = false) TipoProduto tipo) {
        if (tipo != null) return ResponseEntity.ok(produtoService.listarPorTipo(tipo));
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    public ResponseEntity<ProdutoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar produto")
    public ResponseEntity<ProdutoResponse> atualizar(@PathVariable Long id,
                                                     @Valid @RequestBody ProdutoRequest request) {
        return ResponseEntity.ok(produtoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Inativar produto")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        produtoService.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/estoque/movimentacao")
    @Operation(summary = "Registrar entrada ou saída de estoque")
    public ResponseEntity<MovimentacaoEstoqueResponse> movimentar(
            @Valid @RequestBody MovimentacaoEstoqueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(produtoService.registrarMovimentacao(request));
    }

    @GetMapping("/{id}/estoque/movimentacoes")
    @Operation(summary = "Listar movimentações de estoque do produto")
    public ResponseEntity<List<MovimentacaoEstoqueResponse>> listarMovimentacoes(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.listarMovimentacoes(id));
    }

    @GetMapping("/estoque/movimentacoes")
    @Operation(summary = "Listar todas as movimentações de estoque")
    public ResponseEntity<List<MovimentacaoEstoqueResponse>> listarTodasMovimentacoes() {
        return ResponseEntity.ok(produtoService.listarTodasMovimentacoes());
    }
}
