package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.enums.TipoDocumento;
import br.com.fiap.oficina.dto.request.ClienteRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClienteControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private Long criarCliente(String nome, String cpf) throws Exception {
        ClienteRequest req = new ClienteRequest(nome, cpf, TipoDocumento.CPF, null, null, null);
        MvcResult result = mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("Deve criar cliente via POST /api/clientes")
    void deveCriarCliente() throws Exception {
        ClienteRequest request = new ClienteRequest(
                "Maria Souza", "98765432100",
                TipoDocumento.CPF, "11988887777",
                "maria@email.com", "Av. Paulista, 100"
        );

        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.cpfCnpj").value("98765432100"));
    }

    @Test
    @DisplayName("Deve retornar 422 ao criar cliente com CPF duplicado")
    void deveRetornar422CpfDuplicado() throws Exception {
        ClienteRequest request = new ClienteRequest(
                "Pedro Lima", "11122233344",
                TipoDocumento.CPF, null, null, null
        );

        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve retornar 400 ao criar cliente com dados inválidos")
    void deveRetornar400DadosInvalidos() throws Exception {
        ClienteRequest request = new ClienteRequest(
                "", "123", null, null, null, null
        );

        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve listar clientes")
    void deveListarClientes() throws Exception {
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Deve buscar cliente por ID")
    void deveBuscarPorId() throws Exception {
        Long id = criarCliente("Ana Paula", "22233344455");

        mockMvc.perform(get("/api/clientes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Paula"));
    }

    @Test
    @DisplayName("Deve retornar 404 ao buscar cliente inexistente")
    void deveRetornar404ClienteInexistente() throws Exception {
        mockMvc.perform(get("/api/clientes/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve buscar cliente por CPF/CNPJ")
    void deveBuscarPorCpfCnpj() throws Exception {
        criarCliente("Bruno Correia", "33344455566");

        mockMvc.perform(get("/api/clientes/cpf-cnpj/33344455566"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bruno Correia"));
    }

    @Test
    @DisplayName("Deve atualizar cliente existente")
    void deveAtualizarCliente() throws Exception {
        Long id = criarCliente("Carlos Mendes", "44455566677");

        ClienteRequest update = new ClienteRequest(
                "Carlos Mendes Atualizado", "44455566677",
                TipoDocumento.CPF, "11977776666", "carlos@email.com", null
        );

        mockMvc.perform(put("/api/clientes/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Carlos Mendes Atualizado"));
    }

    @Test
    @DisplayName("Deve deletar cliente existente")
    void deveDeletarCliente() throws Exception {
        Long id = criarCliente("Diana Faria", "55566677788");

        mockMvc.perform(delete("/api/clientes/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/clientes/" + id))
                .andExpect(status().isNotFound());
    }
}
