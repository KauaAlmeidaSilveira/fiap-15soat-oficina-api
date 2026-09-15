package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MinhasOrdensServicoControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private Long clienteAna;
    private Long osAna;
    private Long osCarlos;

    @BeforeEach
    void criarDados() throws Exception {
        clienteAna = criar("/api/clientes", new ClienteRequest("Ana Lima", "71498053297",
                TipoDocumento.CPF, null, "ana@email.com", null));
        Long clienteCarlos = criar("/api/clientes", new ClienteRequest("Carlos Mendes", "52998224725",
                TipoDocumento.CPF, null, "carlos@email.com", null));
        Long veiculo = criar("/api/veiculos", new VeiculoRequest("XYZ9876", "Honda", "Civic", 2022, "Preto", null));

        osAna = criar("/api/ordens-servico", new OrdemServicoRequest(clienteAna, veiculo, "Motor falhando", null, null));
        osCarlos = criar("/api/ordens-servico", new OrdemServicoRequest(clienteCarlos, veiculo, "Freio rangendo", null, null));
    }

    private Long criar(String rota, Object corpo) throws Exception {
        String resposta = mockMvc.perform(post(rota)
                        .with(user("recepcao").roles("ADMIN", "RECEPCAO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asLong();
    }

    private RequestPostProcessor tokenDoCliente(Long clienteId) {
        return jwt().jwt(j -> j.subject(String.valueOf(clienteId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    @Test
    @DisplayName("Cliente lista apenas as próprias ordens de serviço")
    void clienteListaSomenteAsProprias() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico").with(tokenDoCliente(clienteAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(osAna));
    }

    @Test
    @DisplayName("Cliente consulta o detalhe de uma OS própria")
    void clienteConsultaOsPropria() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico/{id}", osAna).with(tokenDoCliente(clienteAna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(osAna))
                .andExpect(jsonPath("$.status").value("RECEBIDA"));
    }

    @Test
    @DisplayName("OS de outro cliente responde 404, sem revelar que existe")
    void osDeOutroClienteResponde404() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico/{id}", osCarlos).with(tokenDoCliente(clienteAna)))
                .andExpect(status().isNotFound());
    }
}
