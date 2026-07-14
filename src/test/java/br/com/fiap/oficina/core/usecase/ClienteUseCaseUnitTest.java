package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteUseCaseUnitTest {

    @Mock ClienteGateway clienteGateway;
    ClienteUseCase clienteUseCase;

    private Cliente clienteExistente;

    @BeforeEach
    void setup() {
        clienteUseCase = new ClienteUseCase(clienteGateway);
        clienteExistente = Cliente.builder()
                .id(1L)
                .nome("João Silva")
                .cpfCnpj("12345678901")
                .tipoDocumento(TipoDocumento.CPF)
                .email("joao@email.com")
                .criadoEm(LocalDateTime.now())
                .build();
    }

    private Cliente novoCliente() {
        return Cliente.builder()
                .nome("João Silva")
                .cpfCnpj("12345678901")
                .tipoDocumento(TipoDocumento.CPF)
                .telefone("11999999999")
                .email("joao@email.com")
                .endereco("Rua das Flores, 10")
                .build();
    }

    @Test
    @DisplayName("Deve criar cliente com sucesso")
    void deveCriarCliente() {
        when(clienteGateway.existePorCpfCnpj(anyString())).thenReturn(false);
        when(clienteGateway.salvar(any(Cliente.class))).thenReturn(clienteExistente);

        Cliente criado = clienteUseCase.criar(novoCliente());

        assertThat(criado).isNotNull();
        assertThat(criado.getNome()).isEqualTo("João Silva");
        verify(clienteGateway).salvar(any(Cliente.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar cliente com CPF já existente")
    void deveLancarExcecaoCpfDuplicado() {
        when(clienteGateway.existePorCpfCnpj(anyString())).thenReturn(true);

        assertThatThrownBy(() -> clienteUseCase.criar(novoCliente()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("12345678901");
    }

    @Test
    @DisplayName("Deve buscar cliente por ID")
    void deveBuscarPorId() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));

        Cliente encontrado = clienteUseCase.buscarPorId(1L);

        assertThat(encontrado.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar ID inexistente")
    void deveLancarExcecaoIdInexistente() {
        when(clienteGateway.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteUseCase.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve listar todos os clientes")
    void deveListarTodos() {
        when(clienteGateway.listarTodos()).thenReturn(List.of(clienteExistente));

        List<Cliente> lista = clienteUseCase.listarTodos();

        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Deve atualizar cliente existente")
    void deveAtualizar() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        when(clienteGateway.salvar(any(Cliente.class))).thenReturn(clienteExistente);

        Cliente dados = novoCliente();
        dados.setNome("João Atualizado");

        Cliente atualizado = clienteUseCase.atualizar(1L, dados);

        assertThat(atualizado).isNotNull();
        verify(clienteGateway).salvar(any(Cliente.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar com CPF de outro cliente")
    void deveLancarExcecaoAtualizarCpfDeOutro() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        when(clienteGateway.existePorCpfCnpj("99999999999")).thenReturn(true);

        Cliente dados = novoCliente();
        dados.setCpfCnpj("99999999999");

        assertThatThrownBy(() -> clienteUseCase.atualizar(1L, dados))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Deve deletar cliente existente")
    void deveDeletar() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        doNothing().when(clienteGateway).deletarPorId(1L);

        assertThatCode(() -> clienteUseCase.deletar(1L)).doesNotThrowAnyException();
        verify(clienteGateway).deletarPorId(1L);
    }
}
