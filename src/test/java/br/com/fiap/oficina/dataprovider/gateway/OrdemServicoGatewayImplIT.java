package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrdemServicoGatewayImplIT {

    @Autowired OrdemServicoGateway ordemServicoGateway;
    @Autowired ClienteGateway clienteGateway;
    @Autowired VeiculoGateway veiculoGateway;

    private OrdemServico novaOsSalva() {
        Cliente cliente = clienteGateway.salvar(Cliente.builder()
                .nome("Carlos Mendes")
                .cpfCnpj("52998224725")
                .tipoDocumento(TipoDocumento.CPF)
                .build());

        Veiculo veiculo = veiculoGateway.salvar(Veiculo.builder()
                .placa("KLM1234")
                .marca("Fiat")
                .modelo("Argo")
                .ano(2021)
                .cor("Branco")
                .build());

        return ordemServicoGateway.salvar(OrdemServico.builder()
                .numero("OS-TESTE-1")
                .cliente(cliente)
                .veiculo(veiculo)
                .status(StatusOS.RECEBIDA)
                .itens(new ArrayList<>())
                .build());
    }

    @Test
    @DisplayName("Deve persistir o instante da mudança de status entre gravações")
    void devePersistirStatusAlteradoEm() {
        OrdemServico os = novaOsSalva();
        os.avancarStatus();

        ordemServicoGateway.salvar(os);

        OrdemServico relida = ordemServicoGateway.buscarPorId(os.getId()).orElseThrow();
        assertThat(relida.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        // No Linux LocalDateTime.now() tem precisao de nanossegundos, mas a coluna TIMESTAMP
        // guarda so microssegundos: o valor relido do banco nunca e identico ao da memoria.
        assertThat(relida.getStatusAlteradoEm())
                .isNotNull()
                .isCloseTo(os.getStatusAlteradoEm(), within(1, ChronoUnit.MICROS));
    }
}
