package br.com.fiap.oficina.dataprovider.persistence.repository;

import br.com.fiap.oficina.dataprovider.persistence.entity.SaldoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaldoEstoqueRepository extends JpaRepository<SaldoEstoque, Long> {
    Optional<SaldoEstoque> findByProdutoId(Long produtoId);
}
