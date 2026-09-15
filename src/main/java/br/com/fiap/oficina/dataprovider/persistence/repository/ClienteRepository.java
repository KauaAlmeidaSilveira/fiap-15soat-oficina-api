package br.com.fiap.oficina.dataprovider.persistence.repository;

import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByCpfCnpj(String cpfCnpj);
    boolean existsByCpfCnpj(String cpfCnpj);
    List<Cliente> findByStatus(StatusCliente status);
}
