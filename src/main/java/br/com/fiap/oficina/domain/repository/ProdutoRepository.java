package br.com.fiap.oficina.domain.repository;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findByTipo(TipoProduto tipo);
    List<Produto> findByAtivoTrue();
    List<Produto> findByNomeContainingIgnoreCaseAndAtivoTrue(String nome);
}
