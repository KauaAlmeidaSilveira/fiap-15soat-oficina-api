package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
                .status(StatusCliente.ATIVO)
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
    @DisplayName("Deve listar apenas clientes ATIVOS quando nenhum filtro é informado")
    void deveListarAtivosPorPadrao() {
        when(clienteGateway.listarPorStatus(StatusCliente.ATIVO)).thenReturn(List.of(clienteExistente));

        List<Cliente> lista = clienteUseCase.listar(null);

        assertThat(lista).hasSize(1);
        verify(clienteGateway).listarPorStatus(StatusCliente.ATIVO);
    }

    @Test
    @DisplayName("Deve listar pelo status informado quando há filtro")
    void deveListarPeloFiltroInformado() {
        when(clienteGateway.listarPorStatus(StatusCliente.INATIVO)).thenReturn(List.of());

        clienteUseCase.listar(StatusCliente.INATIVO);

        verify(clienteGateway).listarPorStatus(StatusCliente.INATIVO);
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
    @DisplayName("Deve inativar o cliente em vez de apagá-lo")
    void deveInativarClienteExistente() {
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        when(clienteGateway.salvar(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        clienteUseCase.inativar(1L);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteGateway).salvar(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusCliente.INATIVO);
    }

    @Test
    @DisplayName("Inativar cliente já inativo não deve lançar exceção")
    void deveInativarDeFormaIdempotente() {
        clienteExistente.inativar();
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        when(clienteGateway.salvar(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> clienteUseCase.inativar(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Deve lançar exceção ao inativar cliente inexistente")
    void deveLancarExcecaoAoInativarInexistente() {
        when(clienteGateway.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteUseCase.inativar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve criar cliente sempre como ATIVO, ignorando o status recebido")
    void deveCriarSempreAtivo() {
        Cliente entrada = novoCliente();
        entrada.setStatus(StatusCliente.INATIVO);
        when(clienteGateway.existePorCpfCnpj(anyString())).thenReturn(false);
        when(clienteGateway.salvar(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        clienteUseCase.criar(entrada);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteGateway).salvar(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusCliente.ATIVO);
    }

    @Test
    @DisplayName("Atualização cadastral não deve alterar o status do cliente")
    void naoDeveAlterarStatusAoAtualizar() {
        clienteExistente.inativar();
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        when(clienteGateway.salvar(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        Cliente dados = novoCliente();
        dados.setStatus(StatusCliente.ATIVO);

        clienteUseCase.atualizar(1L, dados);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteGateway).salvar(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusCliente.INATIVO);
    }

    @Test
    @DisplayName("Deve reativar cliente via alteração de status")
    void deveAlterarStatus() {
        clienteExistente.inativar();
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(clienteExistente));
        when(clienteGateway.salvar(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        Cliente resultado = clienteUseCase.alterarStatus(1L, StatusCliente.ATIVO);

        assertThat(resultado.getStatus()).isEqualTo(StatusCliente.ATIVO);
    }
}
