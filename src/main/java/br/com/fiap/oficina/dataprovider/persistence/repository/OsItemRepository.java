package br.com.fiap.oficina.dataprovider.persistence.repository;

import br.com.fiap.oficina.dataprovider.persistence.entity.OsItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OsItemRepository extends JpaRepository<OsItem, Long> {
    Optional<OsItem> findByIdAndOrdemServicoId(Long id, Long ordemServicoId);
}