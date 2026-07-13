package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import br.com.fiap.oficina.service.AprovacaoTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = {"ADMIN", "RECEPCAO", "OPERADOR"})
class AprovacaoPublicaControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AprovacaoTokenService tokenService;

    private Long clienteId;
    private Long veiculoId;

    @BeforeEach
    void criarDadosBase() throws Exception {
        ClienteRequest clienteReq = new ClienteRequest("Maria Souza", "98765432100",
                TipoDocumento.CPF, null, "maria@email.com", null);
        MvcResult clienteResult = mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteReq)))
                .andReturn();
        clienteId = objectMapper.readTree(clienteResult.getResponse().getContentAsString())
                .get("id").asLong();

        VeiculoRequest veiculoReq = new VeiculoRequest("DEF5678", "Fiat", "Uno", 2019, "Branco", null);
        MvcResult veiculoResult = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(veiculoReq)))
                .andReturn();
        veiculoId = objectMapper.readTree(veiculoResult.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private Long criarOSEmAguardandoAprovacao() throws Exception {
        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Revisão", null, null);
        MvcResult result = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();
        Long osId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        return osId;
    }

    @Test
    @DisplayName("Deve aprovar a OS ao acessar o link de aprovação com token válido")
    void deveAprovarComTokenValido() throws Exception {
        Long osId = criarOSEmAguardandoAprovacao();
        String token = tokenService.gerarToken(osId, true);

        mockMvc.perform(get("/aprovacao-os").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aprovado")));

        mockMvc.perform(get("/api/ordens-servico/" + osId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("EM_EXECUCAO"));
    }

    @Test
    @DisplayName("Deve recusar a OS ao acessar o link de recusa com token válido")
    void deveRecusarComTokenValido() throws Exception {
        Long osId = criarOSEmAguardandoAprovacao();
        String token = tokenService.gerarToken(osId, false);

        mockMvc.perform(get("/aprovacao-os").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recusado")));

        mockMvc.perform(get("/api/ordens-servico/" + osId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("REPROVADA"));
    }

    @Test
    @DisplayName("Deve retornar 400 para token inválido")
    void deveRetornar400ParaTokenInvalido() throws Exception {
        mockMvc.perform(get("/aprovacao-os").param("token", "isso-nao-e-um-jwt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar 409 ao reutilizar token de OS já processada")
    void deveRetornar409ParaOSJaProcessada() throws Exception {
        Long osId = criarOSEmAguardandoAprovacao();
        String token = tokenService.gerarToken(osId, true);

        mockMvc.perform(get("/aprovacao-os").param("token", token)).andExpect(status().isOk());

        mockMvc.perform(get("/aprovacao-os").param("token", token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Deve retornar 404 para OS inexistente")
    void deveRetornar404ParaOSInexistente() throws Exception {
        String token = tokenService.gerarToken(999999L, true);

        mockMvc.perform(get("/aprovacao-os").param("token", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Endpoint de aprovação por e-mail não deve exigir autenticação")
    void naoDeveExigirAutenticacao() throws Exception {
        Long osId = criarOSEmAguardandoAprovacao();
        String token = tokenService.gerarToken(osId, true);

        mockMvc.perform(get("/aprovacao-os").param("token", token).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isOk());
    }
}
