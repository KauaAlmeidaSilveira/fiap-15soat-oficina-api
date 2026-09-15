package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Usuario;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseUnitTest {

    @Mock UsuarioGateway usuarioGateway;
    @Mock CriptografiaSenhaGateway senhaGateway;

    AuthUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new AuthUseCase(usuarioGateway, senhaGateway);
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
