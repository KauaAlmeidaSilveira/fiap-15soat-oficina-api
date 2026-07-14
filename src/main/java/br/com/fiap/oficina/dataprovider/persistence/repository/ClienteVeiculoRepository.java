package br.com.fiap.oficina.dataprovider.persistence.repository;

import br.com.fiap.oficina.dataprovider.persistence.entity.ClienteVeiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteVeiculoRepository extends JpaRepository<ClienteVeiculo, Long> {
    List<ClienteVeiculo> findByClienteId(Long clienteId);
    List<ClienteVeiculo> findByVeiculoId(Long veiculoId);
    Optional<ClienteVeiculo> findByClienteIdAndVeiculoId(Long clienteId, Long veiculoId);
    boolean existsByClienteIdAndVeiculoId(Long clienteId, Long veiculoId);
}
