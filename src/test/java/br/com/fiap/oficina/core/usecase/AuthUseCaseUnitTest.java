package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Usuario;
import br.com.fiap.oficina.core.domain.exception.CredenciaisInvalidasException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.TokenAutenticacaoGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseUnitTest {

    @Mock UsuarioGateway usuarioGateway;
    @Mock TokenAutenticacaoGateway tokenGateway;
    @Mock CriptografiaSenhaGateway senhaGateway;

    AuthUseCase useCase;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        useCase = new AuthUseCase(usuarioGateway, tokenGateway, senhaGateway);
        usuario = Usuario.builder().id(UUID.randomUUID()).username("joao@email.com")
                .password("hash").roles(Set.of("OPERADOR")).build();
    }

    @Test
    @DisplayName("Login deve gerar token com credenciais válidas")
    void loginComCredenciaisValidas() {
        when(usuarioGateway.buscarPorUsername("joao@email.com")).thenReturn(Optional.of(usuario));
        when(senhaGateway.conferir("Senha@123", "hash")).thenReturn(true);
        when(tokenGateway.gerarToken(eq("joao@email.com"), anyList(), anyLong())).thenReturn("jwt-token");

        AuthUseCase.ResultadoLogin resultado = useCase.login("Joao@email.com", "Senha@123");

        assertThat(resultado.token()).isEqualTo("jwt-token");
        assertThat(resultado.expiresIn()).isEqualTo(28800L);
    }

    @Test
    @DisplayName("Login deve falhar com usuário inexistente")
    void loginUsuarioInexistente() {
        when(usuarioGateway.buscarPorUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.login("naoexiste@email.com", "Senha@123"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("Login deve falhar com senha incorreta")
    void loginSenhaIncorreta() {
        when(usuarioGateway.buscarPorUsername("joao@email.com")).thenReturn(Optional.of(usuario));
        when(senhaGateway.conferir("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.login("joao@email.com", "errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("Register deve criar usuário com perfil OPERADOR")
    void registerComSucesso() {
        when(usuarioGateway.existePorUsername("novo@email.com")).thenReturn(false);
        when(senhaGateway.codificar("Senha@123")).thenReturn("hash-novo");
        when(usuarioGateway.salvar(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario criado = useCase.register("Novo@email.com", "Senha@123");

        assertThat(criado.getUsername()).isEqualTo("novo@email.com");
        assertThat(criado.getRoles()).containsExactly("OPERADOR");
        verify(usuarioGateway).salvar(any(Usuario.class));
    }

    @Test
    @DisplayName("Register deve falhar com usuário já existente")
    void registerUsuarioDuplicado() {
        when(usuarioGateway.existePorUsername("dup@email.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.register("dup@email.com", "Senha@123"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já cadastrado");
    }
}
