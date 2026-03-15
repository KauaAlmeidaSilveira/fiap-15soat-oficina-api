package br.com.fiap.oficina.domain.repository;

import br.com.fiap.oficina.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.domain.model.MovimentacaoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {
    List<MovimentacaoEstoque> findByProdutoIdOrderByCriadoEmDesc(Long produtoId);
    List<MovimentacaoEstoque> findByTipo(TipoMovimentacao tipo);

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN m.tipo = br.com.fiap.oficina.domain.enums.TipoMovimentacao.ENTRADA
                     THEN m.quantidade
                     ELSE -m.quantidade END)
        , 0)
        FROM MovimentacaoEstoque m
        WHERE m.produto.id = :produtoId
    """)
    BigDecimal calcularSaldoPorProduto(@Param("produtoId") Long produtoId);
}
