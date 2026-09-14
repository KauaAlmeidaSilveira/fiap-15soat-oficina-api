package br.com.fiap.oficina.entrypoint.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class CorrelationIdFilterIT {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("Deve devolver um correlation id gerado no header da resposta")
    void deveDevolverCorrelationIdGerado() throws Exception {
        mockMvc.perform(get("/api/clientes"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    @DisplayName("Deve ecoar o correlation id enviado pelo cliente")
    void deveEcoarCorrelationIdRecebido() throws Exception {
        mockMvc.perform(get("/api/clientes").header(CorrelationIdFilter.HEADER, "rastreio-999"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "rastreio-999"));
    }
}
