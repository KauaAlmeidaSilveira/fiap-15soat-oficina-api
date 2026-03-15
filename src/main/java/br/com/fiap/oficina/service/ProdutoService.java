package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.MovimentacaoEstoque;
import br.com.fiap.oficina.domain.model.OrdemServico;
import br.com.fiap.oficina.domain.model.Produto;
import br.com.fiap.oficina.domain.model.SaldoEstoque;
import br.com.fiap.oficina.domain.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.domain.repository.OrdemServicoRepository;
import br.com.fiap.oficina.domain.repository.ProdutoRepository;
import br.com.fiap.oficina.domain.repository.SaldoEstoqueRepository;
import br.com.fiap.oficina.dto.request.MovimentacaoEstoqueRequest;
import br.com.fiap.oficina.dto.request.ProdutoRequest;
import br.com.fiap.oficina.dto.response.MovimentacaoEstoqueResponse;
import br.com.fiap.oficina.dto.response.ProdutoResponse;
import br.com.fiap.oficina.handler.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final OrdemServicoRepository ordemServicoRepository;

    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        Produto produto = Produto.builder()
                .nome(request.nome())
                .descricao(request.descricao())
                .tipo(request.tipo())
                .precoUnitario(request.precoUnitario())
                .unidadeMedida(request.unidadeMedida())
                .ativo(true)
                .build();
        produto = produtoRepository.save(produto);

        if (TipoProduto.PECA.equals(produto.getTipo())) {
            SaldoEstoque saldo = SaldoEstoque.builder()
                    .produto(produto)
                    .quantidade(BigDecimal.ZERO)
                    .build();
            SaldoEstoque savedSaldo = saldoEstoqueRepository.save(saldo);
            produto.setSaldoEstoque(savedSaldo);

            if (request.quantidadeInicial() != null
                    && request.quantidadeInicial().compareTo(BigDecimal.ZERO) > 0) {
                MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                        .produto(produto)
                        .tipo(TipoMovimentacao.ENTRADA)
                        .quantidade(request.quantidadeInicial())
                        .motivo("Estoque inicial - cadastro do produto")
                        .build();
                movimentacaoEstoqueRepository.save(mov);
                sincronizarSaldo(produto.getId());
            }
        }
        return toResponse(produto);
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorId(Long id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarTodos() {
        return produtoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarPorTipo(TipoProduto tipo) {
        return produtoRepository.findByTipo(tipo).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoRequest request) {
        Produto produto = findById(id);
        produto.setNome(request.nome());
        produto.setDescricao(request.descricao());
        produto.setPrecoUnitario(request.precoUnitario());
        produto.setUnidadeMedida(request.unidadeMedida());
        return toResponse(produtoRepository.save(produto));
    }

    @Transactional
    public void inativar(Long id) {
        Produto produto = findById(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional
    public MovimentacaoEstoqueResponse registrarMovimentacao(MovimentacaoEstoqueRequest request) {
        Produto produto = findById(request.produtoId());

        if (!TipoProduto.PECA.equals(produto.getTipo())) {
            throw new RegraDeNegocioException("Controle de estoque é aplicável apenas a peças.");
        }

        if (request.tipo() == TipoMovimentacao.SAIDA) {
            BigDecimal saldoAtual = saldoEstoqueRepository.findByProdutoId(produto.getId())
                    .map(SaldoEstoque::getQuantidade)
                    .orElse(BigDecimal.ZERO);
            if (saldoAtual.compareTo(request.quantidade()) < 0) {
                throw new IllegalStateException(
                        "Saldo insuficiente. Disponível: " + saldoAtual + ", solicitado: " + request.quantidade());
            }
        }

        OrdemServico os = null;
        if (request.ordemServicoId() != null) {
            os = ordemServicoRepository.findById(request.ordemServicoId()).orElse(null);
        }

        MovimentacaoEstoque mov = MovimentacaoEstoque.builder()
                .produto(produto)
                .tipo(request.tipo())
                .quantidade(request.quantidade())
                .motivo(request.motivo())
                .ordemServico(os)
                .build();
        mov = movimentacaoEstoqueRepository.save(mov);
        sincronizarSaldo(produto.getId());
        return toMovResponse(mov);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoEstoqueResponse> listarMovimentacoes(Long produtoId) {
        findById(produtoId);
        return movimentacaoEstoqueRepository.findByProdutoIdOrderByCriadoEmDesc(produtoId)
                .stream().map(this::toMovResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoEstoqueResponse> listarTodasMovimentacoes() {
        return movimentacaoEstoqueRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "criadoEm"))
                .stream().map(this::toMovResponse).toList();
    }

    private Produto findById(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", id));
    }

    public ProdutoResponse toResponse(Produto p) {
        BigDecimal saldo = null;
        if (TipoProduto.PECA.equals(p.getTipo()) && p.getSaldoEstoque() != null) {
            saldo = p.getSaldoEstoque().getQuantidade();
        }
        return new ProdutoResponse(p.getId(), p.getNome(), p.getDescricao(),
                p.getTipo(), p.getPrecoUnitario(), p.getUnidadeMedida(),
                p.getAtivo(), saldo, p.getCriadoEm());
    }

    public void sincronizarSaldo(Long produtoId) {
        saldoEstoqueRepository.findByProdutoId(produtoId).ifPresent(saldo -> {
            BigDecimal calculado = movimentacaoEstoqueRepository.calcularSaldoPorProduto(produtoId);
            saldo.setQuantidade(calculado != null ? calculado : BigDecimal.ZERO);
            saldoEstoqueRepository.save(saldo);
        });
    }

    private MovimentacaoEstoqueResponse toMovResponse(MovimentacaoEstoque m) {
        return new MovimentacaoEstoqueResponse(
                m.getId(),
                m.getProduto().getId(),
                m.getProduto().getNome(),
                m.getTipo(),
                m.getQuantidade(),
                m.getMotivo(),
                m.getOrdemServico() != null ? m.getOrdemServico().getId() : null,
                m.getCriadoEm()
        );
    }
}
