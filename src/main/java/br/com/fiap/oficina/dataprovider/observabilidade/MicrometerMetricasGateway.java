package br.com.fiap.oficina.dataprovider.observabilidade;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class MicrometerMetricasGateway implements MetricasGateway {

    private final MeterRegistry registry;

    @Override
    public void ordemServicoCriada() {
        registry.counter("oficina.os.criadas").increment();
    }

    @Override
    public void transicaoDeStatus(StatusOS de, StatusOS para, Duration tempoNoStatusAnterior) {
        registry.counter("oficina.os.transicoes", "de", de.name(), "para", para.name()).increment();
        if (tempoNoStatusAnterior != null) {
            registry.timer("oficina.os.tempo.status", "status", de.name()).record(tempoNoStatusAnterior);
        }
    }

    @Override
    public void falhaDeIntegracao(String integracao, String motivo) {
        registry.counter("oficina.integracao.falhas", "integracao", integracao, "motivo", motivo).increment();
    }
}
