package br.com.fiap.oficina.domain.repository;

import br.com.fiap.oficina.domain.enums.StatusOS;
import br.com.fiap.oficina.domain.model.OrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {
    Optional<OrdemServico> findByNumero(String numero);
    List<OrdemServico> findByClienteId(Long clienteId);
    List<OrdemServico> findByVeiculoId(Long veiculoId);
    List<OrdemServico> findByStatus(StatusOS status);
    List<OrdemServico> findByClienteIdAndStatus(Long clienteId, StatusOS status);
    List<OrdemServico> findByStatusNotIn(Collection<StatusOS> status);

    @Query("SELECT o FROM OrdemServico o WHERE o.dataFim IS NOT NULL AND o.dataInicio IS NOT NULL")
    List<OrdemServico> findComTempoDeExecucao();

    @Query("SELECT o FROM OrdemServico o WHERE o.criadoEm BETWEEN :inicio AND :fim")
    List<OrdemServico> findByPeriodo(LocalDateTime inicio, LocalDateTime fim);
}
