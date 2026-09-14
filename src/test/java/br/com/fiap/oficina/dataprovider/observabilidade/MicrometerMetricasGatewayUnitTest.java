package br.com.fiap.oficina.dataprovider.observabilidade;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMetricasGatewayUnitTest {

    private SimpleMeterRegistry registry;
    private MicrometerMetricasGateway gateway;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        gateway = new MicrometerMetricasGateway(registry);
    }

    @Test
    @DisplayName("Deve contar ordens de serviço criadas")
    void deveContarOrdensCriadas() {
        gateway.ordemServicoCriada();
        gateway.ordemServicoCriada();

        assertThat(registry.counter("oficina.os.criadas").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Deve contar transições de status com as tags de origem e destino")
    void deveContarTransicoesComTags() {
        gateway.transicaoDeStatus(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO, Duration.ofHours(1));

        assertThat(registry.counter("oficina.os.transicoes",
                "de", "RECEBIDA", "para", "EM_DIAGNOSTICO").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Deve registrar o tempo no status anterior com a tag do status")
    void deveRegistrarTempoNoStatus() {
        gateway.transicaoDeStatus(StatusOS.EM_DIAGNOSTICO, StatusOS.AGUARDANDO_APROVACAO, Duration.ofHours(3));

        var timer = registry.timer("oficina.os.tempo.status", "status", "EM_DIAGNOSTICO");
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.HOURS)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Deve contar falhas de integração com as tags de integração e motivo")
    void deveContarFalhasDeIntegracao() {
        gateway.falhaDeIntegracao("smtp", "MailSendException");

        assertThat(registry.counter("oficina.integracao.falhas",
                "integracao", "smtp", "motivo", "MailSendException").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Não deve registrar tempo quando a duração é nula")
    void naoDeveRegistrarTempoNulo() {
        gateway.transicaoDeStatus(StatusOS.RECEBIDA, StatusOS.EM_DIAGNOSTICO, null);

        assertThat(registry.find("oficina.os.tempo.status").timer()).isNull();
        assertThat(registry.counter("oficina.os.transicoes",
                "de", "RECEBIDA", "para", "EM_DIAGNOSTICO").count()).isEqualTo(1.0);
    }
}
