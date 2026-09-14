package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.enums.StatusOS;

import java.time.Duration;

public interface MetricasGateway {

    void ordemServicoCriada();

    void transicaoDeStatus(StatusOS de, StatusOS para, Duration tempoNoStatusAnterior);

    void falhaDeIntegracao(String integracao, String motivo);
}
