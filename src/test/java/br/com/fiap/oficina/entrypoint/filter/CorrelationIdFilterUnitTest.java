package br.com.fiap.oficina.entrypoint.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterUnitTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    private AtomicReference<String> executar(MockHttpServletRequest req, MockHttpServletResponse res)
            throws Exception {
        AtomicReference<String> visto = new AtomicReference<>();
        FilterChain chain = (a, b) -> visto.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        filter.doFilter(req, res, chain);
        return visto;
    }

    @Test
    @DisplayName("Deve gerar um correlation id quando a requisição não traz o header")
    void deveGerarQuandoAusente() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> visto = executar(new MockHttpServletRequest(), res);

        assertThat(visto.get()).isNotBlank();
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(visto.get());
    }

    @Test
    @DisplayName("Deve reaproveitar o correlation id recebido no header")
    void deveReaproveitarORecebido() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> visto = executar(req, res);

        assertThat(visto.get()).isEqualTo("abc-123");
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Deve gerar um novo id quando o header vem em branco")
    void deveGerarQuandoHeaderEmBranco() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "   ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> visto = executar(req, res);

        assertThat(visto.get()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    @DisplayName("Deve limpar o MDC ao final da requisição")
    void deveLimparMdcAoFinal() throws Exception {
        executar(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Deve limpar o MDC mesmo quando a requisição falha")
    void deveLimparMdcMesmoComFalha() {
        FilterChain chain = (a, b) -> {
            throw new IllegalStateException("falha na requisição");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        } catch (Exception esperada) {
            // a exceção sobe para o handler; o que importa aqui é o MDC ter sido limpo
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
