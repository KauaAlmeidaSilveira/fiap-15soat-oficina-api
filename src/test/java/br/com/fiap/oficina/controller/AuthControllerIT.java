package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.domain.model.Role;
import br.com.fiap.oficina.domain.repository.RoleRepository;
import br.com.fiap.oficina.dto.request.LoginRequest;
import br.com.fiap.oficina.dto.request.RegisterRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = "ADMIN")
class AuthControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RoleRepository roleRepository;

    @BeforeEach
    void setupRoles() {
        Role adminRole = new Role();
        adminRole.setName(Role.Values.ADMIN.name());
        roleRepository.save(adminRole);

        Role operadorRole = new Role();
        operadorRole.setName(Role.Values.OPERADOR.name());
        roleRepository.save(operadorRole);

        Role recepcaoRole = new Role();
        recepcaoRole.setName(Role.Values.RECEPCAO.name());
        roleRepository.save(recepcaoRole);
    }

    @Test
    @DisplayName("Deve registrar usuário com sucesso e retornar 201")
    void deveRegistrarUsuarioComSucesso() throws Exception {
        RegisterRequest request = new RegisterRequest("novo@email.com", "Senha@123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value("novo@email.com"));
    }

    @Test
    @DisplayName("Deve retornar 422 ao tentar registrar usuário já existente")
    void deveRetornar422UsuarioDuplicado() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicado@email.com", "Senha@123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Deve retornar 400 quando username estiver em branco")
    void deveRetornar400UsernameEmBranco() throws Exception {
        RegisterRequest request = new RegisterRequest("", "senha12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.username").isNotEmpty());
    }

    @Test
    @DisplayName("Deve retornar 400 quando senha estiver em branco")
    void deveRetornar400SenhaEmBranco() throws Exception {
        RegisterRequest request = new RegisterRequest("usuario@email.com", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.password").isNotEmpty());
    }

    @Test
    @DisplayName("Deve retornar 400 quando username não for um e-mail válido")
    void deveRetornar400EmailInvalido() throws Exception {
        RegisterRequest request = new RegisterRequest("nao-e-um-email", "senha12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.username").isNotEmpty());
    }

    @Test
    @DisplayName("Deve retornar 400 quando senha for menor que 8 caracteres")
    void deveRetornar400SenhaCurta() throws Exception {
        RegisterRequest request = new RegisterRequest("usuario@email.com", "Ab@1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.password").isNotEmpty());
    }

    @Test
    @DisplayName("Deve retornar 400 quando senha não atender requisitos de força")
    void deveRetornar400SenhaFraca() throws Exception {
        RegisterRequest request = new RegisterRequest("usuario@email.com", "senha12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.password").isNotEmpty());
    }

    @Test
    @DisplayName("Deve retornar 401 ao tentar login com usuário inexistente")
    void deveRetornar401LoginUsuarioInexistente() throws Exception {
        LoginRequest request = new LoginRequest("naoexiste@email.com", "Senha@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("Deve retornar 403 ao tentar registrar sem role ADMIN")
    void deveRetornar403RegistroSemRoleAdmin() throws Exception {
        RegisterRequest request = new RegisterRequest("tentativa@email.com", "Senha@123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve retornar 401 ao tentar login com senha incorreta")
    void deveRetornar401LoginSenhaIncorreta() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("login@email.com", "Senha@123"))))
                .andExpect(status().isCreated());

        LoginRequest request = new LoginRequest("login@email.com", "SenhaErrada@9");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
