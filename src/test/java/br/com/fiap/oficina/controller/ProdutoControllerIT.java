package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.dto.request.MovimentacaoEstoqueRequest;
import br.com.fiap.oficina.dto.request.ProdutoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = {"ADMIN", "OPERADOR"})
class ProdutoControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve criar peça e inicializar saldo zerado")
    void deveCriarPeca() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Vela de Ignição", "Vela NGK original", TipoProduto.PECA,
                new BigDecimal("22.50"), "UN", null);

        mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Vela de Ignição"))
                .andExpect(jsonPath("$.tipo").value("PECA"))
                .andExpect(jsonPath("$.saldoEstoque").value(0));
    }

    @Test
    @DisplayName("Deve criar serviço sem saldo de estoque")
    void deveCriarServico() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Diagnóstico Eletrônico", null, TipoProduto.SERVICO,
                new BigDecimal("120.00"), null, null);

        mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SERVICO"))
                .andExpect(jsonPath("$.saldoEstoque").isEmpty());
    }

    @Test
    @DisplayName("Deve registrar entrada e refletir no saldo")
    void deveRegistrarEntradaEAtualizarSaldo() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Amortecedor Dianteiro", null, TipoProduto.PECA,
                new BigDecimal("350.00"), "UN", null);

        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long produtoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        MovimentacaoEstoqueRequest movReq = new MovimentacaoEstoqueRequest(
                produtoId, TipoMovimentacao.ENTRADA,
                10, "Compra NF-001", null);

        mockMvc.perform(post("/api/produtos/estoque/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(movReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.quantidade").value(10));

        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoEstoque").value(10));
    }

    @Test
    @DisplayName("Deve retornar 409 ao tentar saída maior que saldo")
    void deveRetornar409SaidaSemSaldo() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Disco de Freio", null, TipoProduto.PECA,
                new BigDecimal("180.00"), "UN", null);

        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long produtoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        MovimentacaoEstoqueRequest movReq = new MovimentacaoEstoqueRequest(
                produtoId, TipoMovimentacao.SAIDA,
                5, "Uso em OS", null);

        mockMvc.perform(post("/api/produtos/estoque/saida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(movReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Deve retornar 422 ao movimentar estoque de serviço")
    void deveRetornar422MovimentacaoServico() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Revisão Completa", null, TipoProduto.SERVICO,
                new BigDecimal("250.00"), null, null);

        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        Long produtoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        MovimentacaoEstoqueRequest movReq = new MovimentacaoEstoqueRequest(
                produtoId, TipoMovimentacao.ENTRADA,
                1, null, null);

        mockMvc.perform(post("/api/produtos/estoque/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(movReq)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve inativar produto (soft delete)")
    void deveInativarProduto() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProdutoRequest(
                                "Coxim do Motor", null, TipoProduto.PECA,
                                new BigDecimal("75.00"), "UN", null))))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/produtos/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("Deve buscar produto por ID")
    void deveBuscarPorId() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Rolamento de Roda", null, TipoProduto.PECA,
                new BigDecimal("95.00"), "UN", null);
        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Rolamento de Roda"));
    }

    @Test
    @DisplayName("Deve listar todos os produtos sem filtro")
    void deveListarTodosSemFiltro() throws Exception {
        mockMvc.perform(get("/api/produtos"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve listar produtos com filtro por tipo")
    void deveListarPorTipo() throws Exception {
        mockMvc.perform(get("/api/produtos?tipo=PECA"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/produtos?tipo=SERVICO"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve atualizar produto existente")
    void deveAtualizarProduto() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Sensor de Oxiênio", null, TipoProduto.PECA,
                new BigDecimal("120.00"), "UN", null);
        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        ProdutoRequest update = new ProdutoRequest(
                "Sensor de Oxiênio Premium", "Versão original", TipoProduto.PECA,
                new BigDecimal("145.00"), "UN", null);
        mockMvc.perform(put("/api/produtos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precoUnitario").value(145.00));
    }

    @Test
    @DisplayName("Deve listar todas as movimentações de estoque")
    void deveListarTodasMovimentacoes() throws Exception {
        mockMvc.perform(get("/api/produtos/estoque/movimentacoes"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve listar movimentações de um produto específico")
    void deveListarMovimentacoesDoProducto() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Bucha de Bandeja", null, TipoProduto.PECA,
                new BigDecimal("45.00"), "UN", null);
        MvcResult result = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/produtos/" + id + "/estoque/movimentacoes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPCAO")
    @DisplayName("Deve retornar 403 ao criar produto sem role OPERADOR")
    void deveRetornar403AoCriarProdutoSemPermissao() throws Exception {
        ProdutoRequest req = new ProdutoRequest(
                "Item Negado", null, TipoProduto.PECA,
                new BigDecimal("10.00"), "UN", null);

        mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Deve retornar 403 ao registrar saída manual sem role ADMIN")
    void deveRetornar403AoRegistrarSaidaSemPermissao() throws Exception {
        MovimentacaoEstoqueRequest req = new MovimentacaoEstoqueRequest(
                1L, TipoMovimentacao.SAIDA, 1, "Teste", null);

        mockMvc.perform(post("/api/produtos/estoque/saida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}

