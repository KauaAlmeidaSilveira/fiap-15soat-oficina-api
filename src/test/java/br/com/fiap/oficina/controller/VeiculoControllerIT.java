package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.enums.TipoDocumento;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VeiculoControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve criar veículo com placa no formato antigo")
    void deveCriarVeiculoPlacaAntiga() throws Exception {
        VeiculoRequest req = new VeiculoRequest("ABC1234", "Fiat", "Palio", 2015, "Vermelho", null);

        mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1234"))
                .andExpect(jsonPath("$.marca").value("Fiat"));
    }

    @Test
    @DisplayName("Deve criar veículo com placa Mercosul")
    void deveCriarVeiculoPlacaMercosul() throws Exception {
        VeiculoRequest req = new VeiculoRequest("ABC1D23", "Volkswagen", "Polo", 2023, "Azul", null);

        mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1D23"));
    }

    @Test
    @DisplayName("Deve retornar 422 ao criar veículo com placa duplicada")
    void deveRetornar422PlacaDuplicada() throws Exception {
        VeiculoRequest req = new VeiculoRequest("XYZ5678", "Honda", "Fit", 2018, "Prata", null);

        mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve retornar 400 para placa inválida")
    void deveRetornar400PlacaInvalida() throws Exception {
        VeiculoRequest req = new VeiculoRequest("INVALIDA", "Ford", "Ka", 2020, null, null);

        mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve vincular cliente ao veículo e listar veículos do cliente")
    void deveVincularClienteEListar() throws Exception {
        // Cria cliente
        ClienteRequest clienteReq = new ClienteRequest(
                "Carlos Pereira", "11122233344",
                TipoDocumento.CPF, null, null, null);
        MvcResult clienteResult = mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteReq)))
                .andReturn();
        Long clienteId = objectMapper.readTree(clienteResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Cria veículo
        VeiculoRequest vReq = new VeiculoRequest("LMN4567", "Chevrolet", "Onix", 2021, "Branco", null);
        MvcResult veiculoResult = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andReturn();
        Long veiculoId = objectMapper.readTree(veiculoResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Vincula
        mockMvc.perform(post("/api/veiculos/" + veiculoId + "/clientes/" + clienteId))
                .andExpect(status().isOk());

        // Lista veículos do cliente
        mockMvc.perform(get("/api/veiculos/cliente/" + clienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value("LMN4567"));
    }

    @Test
    @DisplayName("Deve retornar 422 ao vincular cliente já vinculado")
    void deveRetornar422ClienteJaVinculado() throws Exception {
        ClienteRequest clienteReq = new ClienteRequest(
                "Paulo Salave", "55566677700",
                TipoDocumento.CPF, null, null, null);
        MvcResult clienteResult = mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clienteReq)))
                .andReturn();
        Long clienteId = objectMapper.readTree(clienteResult.getResponse().getContentAsString())
                .get("id").asLong();

        VeiculoRequest vReq = new VeiculoRequest("QRS1234", "Renault", "Sandero", 2019, "Preto", null);
        MvcResult veiculoResult = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andReturn();
        Long veiculoId = objectMapper.readTree(veiculoResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/veiculos/" + veiculoId + "/clientes/" + clienteId))
                .andExpect(status().isOk());

        // Tenta vincular novamente
        mockMvc.perform(post("/api/veiculos/" + veiculoId + "/clientes/" + clienteId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve buscar veículo por placa")
    void deveBuscarPorPlaca() throws Exception {
        VeiculoRequest req = new VeiculoRequest("TUV9876", "Nissan", "March", 2017, "Laranja", null);
        mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/veiculos/placa/TUV9876"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("March"));
    }

    @Test
    @DisplayName("Deve retornar 404 ao buscar placa inexistente")
    void deveRetornar404PlacaInexistente() throws Exception {
        mockMvc.perform(get("/api/veiculos/placa/ZZZ9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve buscar veículo por ID")
    void deveBuscarPorId() throws Exception {
        VeiculoRequest req = new VeiculoRequest("QRS1234", "Renault", "Sandero", 2019, "Branco", null);
        MvcResult result = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/veiculos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placa").value("QRS1234"));
    }

    @Test
    @DisplayName("Deve retornar 404 ao buscar veículo inexistente")
    void deveRetornar404VeiculoInexistente() throws Exception {
        mockMvc.perform(get("/api/veiculos/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve listar todos os veículos")
    void deveListarVeiculos() throws Exception {
        mockMvc.perform(get("/api/veiculos"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve atualizar veículo existente")
    void deveAtualizarVeiculo() throws Exception {
        VeiculoRequest req = new VeiculoRequest("UVW5678", "Peugeot", "208", 2021, "Cinza", null);
        MvcResult result = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        VeiculoRequest update = new VeiculoRequest("UVW5678", "Peugeot", "208 Style", 2021, "Preto", null);
        mockMvc.perform(put("/api/veiculos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("208 Style"));
    }

    @Test
    @DisplayName("Deve deletar veículo existente")
    void deveDeletarVeiculo() throws Exception {
        VeiculoRequest req = new VeiculoRequest("DEF3456", "Citroën", "C3", 2018, "Azul", null);
        MvcResult result = mockMvc.perform(post("/api/veiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/veiculos/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/veiculos/" + id))
                .andExpect(status().isNotFound());
    }
}

