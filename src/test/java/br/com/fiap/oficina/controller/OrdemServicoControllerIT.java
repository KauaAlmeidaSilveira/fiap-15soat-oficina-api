package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.enums.TipoDocumento;
import br.com.fiap.oficina.domain.enums.TipoProduto;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.MovimentacaoEstoqueRequest;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = {"ADMIN", "RECEPCAO", "OPERADOR"})
class OrdemServicoControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private Long clienteId;
    private Long veiculoId;

    @BeforeEach
    void criarDadosBase() throws Exception {
        ClienteRequest clienteReq = new ClienteRequest("Ana Lima", "71498053297",
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
                produtoId, 2, null, null);
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
                produtoId, 1, null, "Item adicional");
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

    @Test
    @WithMockUser(roles = "RECEPCAO")
    @DisplayName("Deve retornar 403 ao avançar status sem role OPERADOR")
    void deveRetornar403AoAvancarStatusSemPermissao() throws Exception {
        mockMvc.perform(patch("/api/ordens-servico/99999/avancar-status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "RECEPCAO")
    @DisplayName("Deve retornar 403 ao aprovar OS sem role OPERADOR")
    void deveRetornar403AoAprovarSemPermissao() throws Exception {
        mockMvc.perform(patch("/api/ordens-servico/99999/aprovar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve registrar SAIDA ao adicionar PECA em OS já aprovada")
    void deveRegistrarSaidaAoAdicionarPecaEmOSAprovada() throws Exception {
        ProdutoRequest prodReq = new ProdutoRequest("Pastilha de Freio", null,
                TipoProduto.PECA, new BigDecimal("90.00"), "UN", null);
        MvcResult prodResult = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn();
        Long produtoId = objectMapper.readTree(prodResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/produtos/estoque/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MovimentacaoEstoqueRequest(produtoId, null, 10, "Estoque inicial", null))))
                .andExpect(status().isCreated());

        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Freios", null, null);
        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();
        Long osId = objectMapper.readTree(osResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/aprovar")).andExpect(status().isOk());

        // saldo ainda = 10, nenhuma peça na OS ainda
        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(jsonPath("$.saldoEstoque").value(10));

        // adiciona PECA à OS já aprovada → deve debitar estoque
        OrdemServicoRequest.OsItemRequest item = new OrdemServicoRequest.OsItemRequest(produtoId, 4, null, null);
        mockMvc.perform(post("/api/ordens-servico/" + osId + "/itens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].produtoNome").value("Pastilha de Freio"));

        // saldo deve ter caído de 10 para 6
        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoEstoque").value(6));
    }

    @Test
    @DisplayName("Deve remover item de OS não aprovada")
    void deveRemoverItemDaOS() throws Exception {
        ProdutoRequest prodReq = new ProdutoRequest("Vela de Ignição", null,
                TipoProduto.PECA, new BigDecimal("15.00"), "UN", null);
        MvcResult prodResult = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn();
        Long produtoId = objectMapper.readTree(prodResult.getResponse().getContentAsString()).get("id").asLong();

        OrdemServicoRequest.OsItemRequest item = new OrdemServicoRequest.OsItemRequest(produtoId, 1, null, null);
        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Falha de ignição", null, List.of(item));
        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var osNode = objectMapper.readTree(osResult.getResponse().getContentAsString());
        Long osId = osNode.get("id").asLong();
        Long itemId = osNode.get("itens").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/ordens-servico/" + osId + "/itens/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(jsonPath("$.valorTotal").value(0.0));
    }

    @Test
    @DisplayName("Deve remover item de OS aprovada e estornar estoque da peça")
    void deveRemoverItemComEstornoDeEstoque() throws Exception {
        ProdutoRequest prodReq = new ProdutoRequest("Correia Dentada", null,
                TipoProduto.PECA, new BigDecimal("120.00"), "UN", null);
        MvcResult prodResult = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn();
        Long produtoId = objectMapper.readTree(prodResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/produtos/estoque/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MovimentacaoEstoqueRequest(produtoId, null, 5, "Estoque inicial", null))))
                .andExpect(status().isCreated());

        OrdemServicoRequest.OsItemRequest item = new OrdemServicoRequest.OsItemRequest(produtoId, 2, null, null);
        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Troca de correia", null, List.of(item));
        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();
        var osNode = objectMapper.readTree(osResult.getResponse().getContentAsString());
        Long osId = osNode.get("id").asLong();
        Long itemId = osNode.get("itens").get(0).get("id").asLong();

        // avança até AGUARDANDO_APROVACAO e aprova (gera SAIDA de estoque)
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/aprovar")).andExpect(status().isOk());

        // remove o item — deve estornar as 2 unidades de volta ao estoque
        mockMvc.perform(delete("/api/ordens-servico/" + osId + "/itens/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());

        // saldo deve ter voltado para 5
        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoEstoque").value(5));
    }

    @Test
    @DisplayName("Deve retornar 404 ao remover item inexistente")
    void deveRetornar404AoRemoverItemInexistente() throws Exception {
        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Teste", null, null);
        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();
        Long osId = objectMapper.readTree(osResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/ordens-servico/" + osId + "/itens/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "RECEPCAO")
    @DisplayName("Deve retornar 403 ao remover item sem role OPERADOR")
    void deveRetornar403AoRemoverItemSemPermissao() throws Exception {
        mockMvc.perform(delete("/api/ordens-servico/1/itens/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ciclo completo: adiciona PECA pós-aprovação (debita) e remove (estorna) — saldo volta ao original")
    void deveManteterSaldoAoCicloAdicionarRemoverPecaAposAprovacao() throws Exception {
        ProdutoRequest prodReq = new ProdutoRequest("Amortecedor", null,
                TipoProduto.PECA, new BigDecimal("250.00"), "UN", null);
        MvcResult prodResult = mockMvc.perform(post("/api/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodReq)))
                .andReturn();
        Long produtoId = objectMapper.readTree(prodResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/produtos/estoque/entrada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MovimentacaoEstoqueRequest(produtoId, null, 8, "Estoque inicial", null))))
                .andExpect(status().isCreated());

        // cria OS sem itens e aprova
        OrdemServicoRequest osRequest = new OrdemServicoRequest(clienteId, veiculoId, "Suspensão", null, null);
        MvcResult osResult = mockMvc.perform(post("/api/ordens-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(osRequest)))
                .andReturn();
        Long osId = objectMapper.readTree(osResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/avancar-status")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/ordens-servico/" + osId + "/aprovar")).andExpect(status().isOk());

        // saldo inicial intacto (nenhum item na OS durante aprovação)
        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(jsonPath("$.saldoEstoque").value(8));

        // adiciona PECA pós-aprovação → deve debitar 3 unidades
        OrdemServicoRequest.OsItemRequest item = new OrdemServicoRequest.OsItemRequest(produtoId, 3, null, null);
        MvcResult addResult = mockMvc.perform(post("/api/ordens-servico/" + osId + "/itens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(jsonPath("$.saldoEstoque").value(5)); // 8 - 3 = 5

        Long itemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("itens").get(0).get("id").asLong();

        // remove o mesmo item → deve estornar as 3 unidades
        mockMvc.perform(delete("/api/ordens-servico/" + osId + "/itens/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());

        // saldo deve ter voltado para 8
        mockMvc.perform(get("/api/produtos/" + produtoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoEstoque").value(8));
    }
}

