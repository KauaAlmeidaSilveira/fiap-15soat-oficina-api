package br.com.fiap.oficina.config;

import br.com.fiap.oficina.entrypoint.filter.CorrelationIdFilter;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("default")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:oficina_logjson;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=localhost"
})
class LogbackJsonConfigIT {

    @Test
    @DisplayName("Perfil default deve escrever os logs com o encoder JSON")
    void perfilDefaultDeveUsarEncoderJson() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        var appender = root.getAppender("JSON");
        assertThat(appender)
                .as("o appender JSON deve estar ativo no perfil default")
                .isInstanceOf(ConsoleAppender.class);

        Encoder<?> encoder = ((ConsoleAppender<?>) appender).getEncoder();
        assertThat(encoder).isInstanceOf(LogstashEncoder.class);
    }

    @Test
    @DisplayName("O correlation id do MDC deve sair dentro do JSON do log")
    void correlationIdDeveSairNoJson() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        LogstashEncoder encoder =
                (LogstashEncoder) ((ConsoleAppender<?>) root.getAppender("JSON")).getEncoder();

        LoggingEvent evento = new LoggingEvent();
        evento.setLoggerName("teste");
        evento.setLevel(Level.INFO);
        evento.setMessage("ordem de serviço criada");
        evento.setMDCPropertyMap(Map.of(CorrelationIdFilter.MDC_KEY, "corr-42"));
        evento.setInstant(Instant.now());

        String json = new String(encoder.encode(evento), StandardCharsets.UTF_8);

        assertThat(json)
                .contains("\"" + CorrelationIdFilter.MDC_KEY + "\":\"corr-42\"")
                .contains("\"message\":\"ordem de serviço criada\"");
    }
}
