package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.enums.StatusOS;

import java.util.List;
import java.util.Optional;

public interface OrdemServicoGateway {

    OrdemServico salvar(OrdemServico ordemServico);

    Optional<OrdemServico> buscarPorId(Long id);

    Optional<OrdemServico> buscarPorNumero(String numero);

    List<OrdemServico> listarAtivas();

    List<OrdemServico> listarPorClienteId(Long clienteId);

    List<OrdemServico> listarPorStatus(StatusOS status);

    List<OrdemServico> listarComTempoDeExecucao();
}
