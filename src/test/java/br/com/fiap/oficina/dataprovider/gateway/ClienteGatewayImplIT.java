package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClienteGatewayImplIT {

    private static final String HASH_DEFINIDO_PELA_LAMBDA = "$2a$10$hashDefinidoPelaLambdaNoPrimeiroAcesso1234567890123";

    @Autowired ClienteGateway clienteGateway;
    @Autowired ClienteRepository clienteRepository;

    @Test
    @DisplayName("Atualizar o cadastro do cliente deve preservar a senha definida no primeiro acesso")
    void atualizarClientePreservaSenhaHash() {
        Cliente salvo = clienteGateway.salvar(Cliente.builder()
                .nome("Carlos Mendes")
                .cpfCnpj("52998224725")
                .tipoDocumento(TipoDocumento.CPF)
                .email("carlos@email.com")
                .build());

        var entidade = clienteRepository.findById(salvo.getId()).orElseThrow();
        entidade.setSenhaHash(HASH_DEFINIDO_PELA_LAMBDA);
        clienteRepository.save(entidade);

        clienteGateway.salvar(Cliente.builder()
                .id(salvo.getId())
                .nome("Carlos Mendes Filho")
                .cpfCnpj("52998224725")
                .tipoDocumento(TipoDocumento.CPF)
                .email("carlos@email.com")
                .status(salvo.getStatus())
                .build());

        var recarregado = clienteRepository.findById(salvo.getId()).orElseThrow();
        assertThat(recarregado.getNome()).isEqualTo("Carlos Mendes Filho");
        assertThat(recarregado.getSenhaHash()).isEqualTo(HASH_DEFINIDO_PELA_LAMBDA);
    }
}
