package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.model.Cliente;
import br.com.fiap.oficina.domain.model.ClienteVeiculo;
import br.com.fiap.oficina.domain.model.Veiculo;
import br.com.fiap.oficina.domain.repository.ClienteRepository;
import br.com.fiap.oficina.domain.repository.ClienteVeiculoRepository;
import br.com.fiap.oficina.domain.repository.VeiculoRepository;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import br.com.fiap.oficina.dto.response.VeiculoResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceUnitTest {

    @Mock VeiculoRepository veiculoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ClienteVeiculoRepository clienteVeiculoRepository;
    @InjectMocks VeiculoService veiculoService;

    private Veiculo veiculoExistente;
    private VeiculoRequest request;

    @BeforeEach
    void setup() {
        veiculoExistente = Veiculo.builder()
                .id(1L)
                .placa("ABC1234")
                .marca("Toyota")
                .modelo("Corolla")
                .ano(2020)
                .cor("Prata")
                .criadoEm(LocalDateTime.now())
                .build();

        request = new VeiculoRequest("ABC1234", "Toyota", "Corolla", 2020, "Prata", null);
    }

    @Test
    @DisplayName("Deve criar veículo com sucesso")
    void deveCriarVeiculo() {
        when(veiculoRepository.existsByPlaca("ABC1234")).thenReturn(false);
        when(veiculoRepository.save(any(Veiculo.class))).thenReturn(veiculoExistente);

        VeiculoResponse response = veiculoService.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.placa()).isEqualTo("ABC1234");
        assertThat(response.marca()).isEqualTo("Toyota");
        verify(veiculoRepository).save(any(Veiculo.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar veículo com placa duplicada")
    void deveLancarExcecaoPlacaDuplicada() {
        when(veiculoRepository.existsByPlaca("ABC1234")).thenReturn(true);

        assertThatThrownBy(() -> veiculoService.criar(request))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("ABC1234");
    }

    @Test
    @DisplayName("Deve normalizar placa para uppercase")
    void deveNormalizarPlacaUppercase() {
        VeiculoRequest reqMinuscula = new VeiculoRequest("abc1234", "Toyota", "Corolla", 2020, null, null);
        when(veiculoRepository.existsByPlaca("ABC1234")).thenReturn(false);
        when(veiculoRepository.save(any())).thenReturn(veiculoExistente);

        veiculoService.criar(reqMinuscula);

        verify(veiculoRepository).existsByPlaca("ABC1234");
    }

    @Test
    @DisplayName("Deve buscar veículo por ID")
    void deveBuscarPorId() {
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculoExistente));

        VeiculoResponse response = veiculoService.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.modelo()).isEqualTo("Corolla");
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar veículo inexistente")
    void deveLancarExcecaoIdInexistente() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> veiculoService.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve listar todos os veículos")
    void deveListarTodos() {
        when(veiculoRepository.findAll()).thenReturn(List.of(veiculoExistente));

        List<VeiculoResponse> lista = veiculoService.listarTodos();

        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Deve vincular cliente ao veículo com sucesso")
    void deveVincularCliente() {
        Cliente cliente = Cliente.builder().id(1L).nome("João").build();
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculoExistente));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(clienteVeiculoRepository.existsByClienteIdAndVeiculoId(1L, 1L)).thenReturn(false);
        when(clienteVeiculoRepository.save(any(ClienteVeiculo.class))).thenReturn(new ClienteVeiculo());

        assertThatCode(() -> veiculoService.vincularCliente(1L, 1L)).doesNotThrowAnyException();
        verify(clienteVeiculoRepository).save(any(ClienteVeiculo.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao vincular cliente já vinculado")
    void deveLancarExcecaoClienteJaVinculado() {
        Cliente cliente = Cliente.builder().id(1L).nome("João").build();
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculoExistente));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(clienteVeiculoRepository.existsByClienteIdAndVeiculoId(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> veiculoService.vincularCliente(1L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já vinculado");
    }

    @Test
    @DisplayName("Deve listar veículos por cliente")
    void deveListarPorCliente() {
        ClienteVeiculo cv = ClienteVeiculo.builder().veiculo(veiculoExistente).build();
        when(clienteVeiculoRepository.findByClienteId(1L)).thenReturn(List.of(cv));

        List<VeiculoResponse> lista = veiculoService.listarPorCliente(1L);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).placa()).isEqualTo("ABC1234");
    }
}
