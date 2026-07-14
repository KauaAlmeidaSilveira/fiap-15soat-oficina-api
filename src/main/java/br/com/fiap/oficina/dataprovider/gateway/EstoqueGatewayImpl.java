package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.gateway.EstoqueGateway;
import br.com.fiap.oficina.dataprovider.persistence.entity.SaldoEstoque;
import br.com.fiap.oficina.dataprovider.persistence.mapper.MovimentacaoEstoquePersistenceMapper;
import br.com.fiap.oficina.dataprovider.persistence.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OrdemServicoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.SaldoEstoqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EstoqueGatewayImpl implements EstoqueGateway {

    private final ProdutoRepository produtoRepository;
    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final MovimentacaoEstoquePersistenceMapper mapper;

    @Override
    @Transactional
    public void criarSaldoInicial(Long produtoId) {
        var produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", produtoId));
        var saldo = saldoEstoqueRepository.save(SaldoEstoque.builder()
                .produto(produto)
                .quantidade(0)
                .build());
        // Sincroniza o lado inverso: sob open-session-in-view a mesma instância de Produto
        // é reusada na releitura posterior; sem isto o saldo recém-criado viria null.
        produto.setSaldoEstoque(saldo);
    }

    @Override
    @Transactional(readOnly = true)
    public int obterSaldoAtual(Long produtoId) {
        return saldoEstoqueRepository.findByProdutoId(produtoId)
                .map(SaldoEstoque::getQuantidade)
                .orElse(0);
    }

    @Override
    @Transactional
    public MovimentacaoEstoque registrarMovimentacao(Long produtoId, TipoMovimentacao tipo,
                                                     Integer quantidade, String motivo, Long ordemServicoId) {
        var produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", produtoId));
        var os = ordemServicoId != null
                ? ordemServicoRepository.findById(ordemServicoId).orElse(null)
                : null;
        var mov = br.com.fiap.oficina.dataprovider.persistence.entity.MovimentacaoEstoque.builder()
                .produto(produto)
                .tipo(tipo)
                .quantidade(quantidade)
                .motivo(motivo)
                .ordemServico(os)
                .build();
        return mapper.toDomain(movimentacaoEstoqueRepository.save(mov));
    }

    @Override
    @Transactional
    public void sincronizarSaldo(Long produtoId) {
        saldoEstoqueRepository.findByProdutoId(produtoId).ifPresent(saldo -> {
            Integer calculado = movimentacaoEstoqueRepository.calcularSaldoPorProduto(produtoId);
            saldo.setQuantidade(calculado != null ? calculado : 0);
            saldoEstoqueRepository.save(saldo);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimentacaoEstoque> listarMovimentacoesPorProduto(Long produtoId) {
        return movimentacaoEstoqueRepository.findByProdutoIdOrderByCriadoEmDesc(produtoId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimentacaoEstoque> listarTodasMovimentacoes() {
        return movimentacaoEstoqueRepository.findAll(Sort.by(Sort.Direction.DESC, "criadoEm"))
                .stream().map(mapper::toDomain).toList();
    }
}
