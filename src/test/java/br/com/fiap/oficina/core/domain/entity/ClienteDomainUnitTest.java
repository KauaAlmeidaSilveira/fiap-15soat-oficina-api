package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteDomainUnitTest {

    private Cliente clienteAtivo() {
        return Cliente.builder().nome("João").cpfCnpj("12345678901").status(StatusCliente.ATIVO).build();
    }

    @Test
    @DisplayName("Deve marcar o cliente como INATIVO ao inativar")
    void deveInativar() {
        Cliente cliente = clienteAtivo();
        cliente.inativar();
        assertThat(cliente.getStatus()).isEqualTo(StatusCliente.INATIVO);
        assertThat(cliente.estaAtivo()).isFalse();
    }

    @Test
    @DisplayName("Deve marcar o cliente como ATIVO ao ativar")
    void deveAtivar() {
        Cliente cliente = clienteAtivo();
        cliente.inativar();
        cliente.ativar();
        assertThat(cliente.getStatus()).isEqualTo(StatusCliente.ATIVO);
        assertThat(cliente.estaAtivo()).isTrue();
    }

    @Test
    @DisplayName("Inativar cliente já inativo deve ser idempotente")
    void deveInativarDeFormaIdempotente() {
        Cliente cliente = clienteAtivo();
        cliente.inativar();
        cliente.inativar();
        assertThat(cliente.getStatus()).isEqualTo(StatusCliente.INATIVO);
    }

    @Test
    @DisplayName("Cliente sem status definido não deve ser considerado ativo")
    void naoDeveConsiderarAtivoSemStatus() {
        Cliente cliente = Cliente.builder().nome("Maria").build();
        assertThat(cliente.estaAtivo()).isFalse();
    }
}
