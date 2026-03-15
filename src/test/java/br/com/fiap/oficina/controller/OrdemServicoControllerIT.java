package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.enums.TipoDocumento;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.OrdemServicoRequest;
import br.com.fiap.oficina.dto.request.ProdutoRequest;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrdemServicoControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private Long clienteId;
    private Long veiculoId;

    @BeforeEach
    void criarDadosBase() throws Exception {
        ClienteRequest clienteReq = new ClienteRequest("Ana Lima", "55566677788",
                TipoDocumento.CPF, null, null, null);
        MvcResult clienteResult = mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteReq)))
                .andReturn();
        clienteId = objectMapper.readTree(clienteResult.getResponse().getContentAsString())
                .get("id").asLong();

        VeiculoRequest veiculoReq = new VeiculoRequest("XYZ9876", "Honda", "Civic", 2022, "Preto", null);
        MvcResult veiculoResult = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(veiculoReq)))
                .andReturn();
        veiculoId = objectMapper.readTree(veiculoResult.getResponse().getContentAsString())
                .get("id").asLong();
    }

    @Test
    @DisplayName("Deve criar OS e avançar status até ENTREGUE")
    void deveCriarOSEAvancarStatusCompleto() throws Exception {
        OrdemServicoRequest osRequest = new OrdemServicoRequest(
                clienteId, veiculoId, "Motor falhando", null, null);

        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andReturn();

        Long osId = objectMapper.readTree(osResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"));

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/aprovar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_EXECUCAO"));

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADA"));

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"));
    }

    @Test
    @DisplayName("Deve criar OS com itens e calcular valor total")
    void deveCriarOSComItens() throws Exception {
        ProdutoRequest prodReq = new ProdutoRequest("Filtro de Ar", null,
                TipoProduto.PECA, new BigDecimal("30.00"), "UN", null);
        MvcResult prodResult = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn();
        Long produtoId = objectMapper.readTree(prodResult.getResponse().getContentAsString())
                .get("id").asLong();

        OrdemServicoRequest.OsItemRequest item = new OrdemServicoRequest.OsItemRequest(
                produtoId, new BigDecimal("2"), null, null);
        OrdemServicoRequest osRequest = new OrdemServicoRequest(
                clienteId, veiculoId, "Revisão completa", null, List.of(item));

        mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens[0].produtoNome").value("Filtro de Ar"))
                .andExpect(jsonPath("$.valorTotal").value(60.00));
    }

    @Test
    @DisplayName("Deve buscar OS por número")
    void deveBuscarOSPorNumero() throws Exception {
        OrdemServicoRequest osRequest = new OrdemServicoRequest(
                clienteId, veiculoId, "Problema elétrico", null, null);

        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();

        String numero = objectMapper.readTree(osResult.getResponse().getContentAsString())
                .get("numero").asText();

        mockMvc.perform(get("/api/ordens-servico/numero/" + numero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(numero));
    }

    @Test
    @DisplayName("Deve retornar 404 ao buscar OS inexistente")
    void deveRetornar404OSInexistente() throws Exception {
        mockMvc.perform(get("/api/ordens-servico/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve listar todas as OS")
    void deveListarTodasOS() throws Exception {
        mockMvc.perform(get("/api/ordens-servico"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve listar OS por status")
    void deveListarPorStatus() throws Exception {
        mockMvc.perform(get("/api/ordens-servico?status=RECEBIDA"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve listar OS por cliente")
    void deveListarPorCliente() throws Exception {
        mockMvc.perform(get("/api/ordens-servico?clienteId=" + clienteId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve adicionar item à OS existente")
    void deveAdicionarItemOS() throws Exception {
        ProdutoRequest prodReq = new ProdutoRequest("Filtro Habitáculo", null,
                TipoProduto.PECA, new BigDecimal("28.00"), "UN", null);
        MvcResult prodResult = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn();
        Long produtoId = objectMapper.readTree(prodResult.getResponse().getContentAsString()).get("id").asLong();

        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Vibração", null, null);
        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();
        Long osId = objectMapper.readTree(osResult.getResponse().getContentAsString()).get("id").asLong();

        OrdemServicoRequest.OsItemRequest item = new OrdemServicoRequest.OsItemRequest(
                produtoId, new BigDecimal("1"), null, "Item adicional");
        mockMvc.perform(post("/api/ordens-servico/" + osId + "/itens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].produtoNome").value("Filtro Habitáculo"));
    }

    @Test
    @DisplayName("Deve retornar métrica de tempo médio")
    void deveRetornarTempoMedio() throws Exception {
        mockMvc.perform(get("/api/ordens-servico/metricas/tempo-medio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tempoMedioHoras").exists());
    }
}

