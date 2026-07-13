package br.com.fiap.oficina.config;

import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import br.com.fiap.oficina.core.usecase.ClienteUseCase;
import br.com.fiap.oficina.core.usecase.VeiculoUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public ClienteUseCase clienteUseCase(ClienteGateway clienteGateway) {
        return new ClienteUseCase(clienteGateway);
    }

    @Bean
    public VeiculoUseCase veiculoUseCase(VeiculoGateway veiculoGateway, ClienteGateway clienteGateway) {
        return new VeiculoUseCase(veiculoGateway, clienteGateway);
    }
}
