package br.com.fiap.oficina.service;

import br.com.fiap.oficina.dataprovider.persistence.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.SaldoEstoqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;

    @Transactional
    public void sincronizarSaldo(Long produtoId) {
        saldoEstoqueRepository.findByProdutoId(produtoId).ifPresent(saldo -> {
            Integer calculado = movimentacaoEstoqueRepository.calcularSaldoPorProduto(produtoId);
            saldo.setQuantidade(calculado != null ? calculado : 0);
            saldoEstoqueRepository.save(saldo);
        });
    }
}
