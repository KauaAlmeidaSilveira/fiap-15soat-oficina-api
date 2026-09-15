package br.com.fiap.oficina.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AutorizacaoPorPerfilIT {

    @Autowired MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/clientes",
            "/api/clientes/1",
            "/api/clientes/cpf-cnpj/52998224725",
            "/api/ordens-servico",
            "/api/ordens-servico/1",
            "/api/ordens-servico/numero/OS1",
            "/api/ordens-servico/metricas/tempo-medio",
            "/api/produtos",
            "/api/produtos/1",
            "/api/produtos/1/estoque/movimentacoes",
            "/api/produtos/estoque/movimentacoes",
            "/api/veiculos",
            "/api/veiculos/1",
            "/api/veiculos/placa/ABC1234",
            "/api/veiculos/cliente/1"
    })
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("Cliente autenticado não acessa rotas de funcionário")
    void clienteRecebe403EmRotaDeFuncionario(String rota) throws Exception {
        mockMvc.perform(get(rota)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Funcionário não acessa as rotas de minhas ordens de serviço")
    void funcionarioRecebe403EmMinhasOrdensServico() throws Exception {
        mockMvc.perform(get("/api/minhas-ordens-servico")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Cadastro de funcionário exige autenticação")
    void registroSemTokenRecebe401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"novo@email.com\",\"password\":\"Senha@123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
