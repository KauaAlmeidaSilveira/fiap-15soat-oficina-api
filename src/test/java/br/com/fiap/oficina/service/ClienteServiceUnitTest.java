package br.com.fiap.oficina.service;

import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.response.ClienteResponse;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class ClienteServiceUnitTest {

    @Mock ClienteRepository clienteRepository;
    @InjectMocks ClienteService clienteService;

    private Cliente clienteExistente;
    private ClienteRequest request;

    @BeforeEach
    void setup() {
        clienteExistente = Cliente.builder()
                .id(1L)
                .nome("João Silva")
                .cpfCnpj("12345678901")
                .tipoDocumento(TipoDocumento.CPF)
                .email("joao@email.com")
                .criadoEm(LocalDateTime.now())
                .build();

        request = new ClienteRequest(
                "João Silva", "12345678901",
                TipoDocumento.CPF, "11999999999",
                "joao@email.com", "Rua das Flores, 10"
        );
    }

    @Test
    @DisplayName("Deve criar cliente com sucesso")
    void deveCriarCliente() {
        when(clienteRepository.existsByCpfCnpj(anyString())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenReturn(clienteExistente);

        ClienteResponse response = clienteService.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.nome()).isEqualTo("João Silva");
        verify(clienteRepository).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar cliente com CPF já existente")
    void deveLancarExcecaoCpfDuplicado() {
        when(clienteRepository.existsByCpfCnpj(anyString())).thenReturn(true);

        assertThatThrownBy(() -> clienteService.criar(request))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("12345678901");
    }

    @Test
    @DisplayName("Deve buscar cliente por ID")
    void deveBuscarPorId() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteExistente));

        ClienteResponse response = clienteService.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar ID inexistente")
    void deveLancarExcecaoIdInexistente() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve listar todos os clientes")
    void deveListarTodos() {
        when(clienteRepository.findAll()).thenReturn(List.of(clienteExistente));

        List<ClienteResponse> lista = clienteService.listarTodos();

        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Deve deletar cliente existente")
    void deveDeletar() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteExistente));
        doNothing().when(clienteRepository).deleteById(1L);

        assertThatCode(() -> clienteService.deletar(1L)).doesNotThrowAnyException();
        verify(clienteRepository).deleteById(1L);
    }
}
