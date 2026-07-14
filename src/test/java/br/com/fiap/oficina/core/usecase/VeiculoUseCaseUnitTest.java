package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoUseCaseUnitTest {

    @Mock VeiculoGateway veiculoGateway;
    @Mock ClienteGateway clienteGateway;
    VeiculoUseCase veiculoUseCase;

    private Veiculo veiculoExistente;

    @BeforeEach
    void setup() {
        veiculoUseCase = new VeiculoUseCase(veiculoGateway, clienteGateway);
        veiculoExistente = Veiculo.builder()
                .id(1L).placa("ABC1234").marca("Toyota").modelo("Corolla")
                .ano(2020).cor("Prata").criadoEm(LocalDateTime.now())
                .build();
    }

    private Veiculo novoVeiculo(String placa) {
        return Veiculo.builder()
                .placa(placa).marca("Toyota").modelo("Corolla").ano(2020).cor("Prata")
                .build();
    }

    @Test
    @DisplayName("Deve criar veículo com sucesso")
    void deveCriarVeiculo() {
        when(veiculoGateway.existePorPlaca("ABC1234")).thenReturn(false);
        when(veiculoGateway.salvar(any(Veiculo.class))).thenReturn(veiculoExistente);

        Veiculo criado = veiculoUseCase.criar(novoVeiculo("ABC1234"));

        assertThat(criado).isNotNull();
        assertThat(criado.getPlaca()).isEqualTo("ABC1234");
        verify(veiculoGateway).salvar(any(Veiculo.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar veículo com placa duplicada")
    void deveLancarExcecaoPlacaDuplicada() {
        when(veiculoGateway.existePorPlaca("ABC1234")).thenReturn(true);

        assertThatThrownBy(() -> veiculoUseCase.criar(novoVeiculo("ABC1234")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("ABC1234");
    }

    @Test
    @DisplayName("Deve normalizar placa para uppercase")
    void deveNormalizarPlacaUppercase() {
        when(veiculoGateway.existePorPlaca("ABC1234")).thenReturn(false);
        when(veiculoGateway.salvar(any())).thenReturn(veiculoExistente);

        veiculoUseCase.criar(novoVeiculo("abc1234"));

        verify(veiculoGateway).existePorPlaca("ABC1234");
    }

    @Test
    @DisplayName("Deve buscar veículo por ID")
    void deveBuscarPorId() {
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculoExistente));

        Veiculo encontrado = veiculoUseCase.buscarPorId(1L);

        assertThat(encontrado.getId()).isEqualTo(1L);
        assertThat(encontrado.getModelo()).isEqualTo("Corolla");
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar veículo inexistente")
    void deveLancarExcecaoIdInexistente() {
        when(veiculoGateway.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> veiculoUseCase.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve listar todos os veículos")
    void deveListarTodos() {
        when(veiculoGateway.listarTodos()).thenReturn(List.of(veiculoExistente));

        List<Veiculo> lista = veiculoUseCase.listarTodos();

        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Deve vincular cliente ao veículo com sucesso")
    void deveVincularCliente() {
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculoExistente));
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(Cliente.builder().id(1L).nome("João").build()));
        when(veiculoGateway.existeVinculo(1L, 1L)).thenReturn(false);

        assertThatCode(() -> veiculoUseCase.vincularCliente(1L, 1L)).doesNotThrowAnyException();
        verify(veiculoGateway).vincularCliente(1L, 1L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao vincular cliente já vinculado")
    void deveLancarExcecaoClienteJaVinculado() {
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculoExistente));
        when(clienteGateway.buscarPorId(1L)).thenReturn(Optional.of(Cliente.builder().id(1L).nome("João").build()));
        when(veiculoGateway.existeVinculo(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> veiculoUseCase.vincularCliente(1L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já vinculado");
    }

    @Test
    @DisplayName("Deve listar veículos por cliente")
    void deveListarPorCliente() {
        when(veiculoGateway.listarPorClienteId(1L)).thenReturn(List.of(veiculoExistente));

        List<Veiculo> lista = veiculoUseCase.listarPorCliente(1L);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getPlaca()).isEqualTo("ABC1234");
    }

    @Test
    @DisplayName("Deve atualizar veículo com nova placa disponível")
    void deveAtualizarVeiculoComPlacaDiferente() {
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculoExistente));
        when(veiculoGateway.existePorPlaca("XYZ9999")).thenReturn(false);
        Veiculo atualizado = Veiculo.builder().id(1L).placa("XYZ9999").marca("Toyota").modelo("Camry").ano(2022).cor("Branco").build();
        when(veiculoGateway.salvar(any(Veiculo.class))).thenReturn(atualizado);

        Veiculo resultado = veiculoUseCase.atualizar(1L, novoVeiculo("XYZ9999"));

        assertThat(resultado.getPlaca()).isEqualTo("XYZ9999");
        verify(veiculoGateway).salvar(any(Veiculo.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao atualizar veículo com placa já usada por outro")
    void deveLancarExcecaoAoAtualizarComPlacaDuplicada() {
        when(veiculoGateway.buscarPorId(1L)).thenReturn(Optional.of(veiculoExistente));
        when(veiculoGateway.existePorPlaca("XYZ9999")).thenReturn(true);

        assertThatThrownBy(() -> veiculoUseCase.atualizar(1L, novoVeiculo("XYZ9999")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Placa já cadastrada");
    }
}
