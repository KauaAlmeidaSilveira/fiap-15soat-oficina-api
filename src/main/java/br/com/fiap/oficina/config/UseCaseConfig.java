package br.com.fiap.oficina.config;

import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.usecase.ClienteUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public ClienteUseCase clienteUseCase(ClienteGateway clienteGateway) {
        return new ClienteUseCase(clienteGateway);
    }
}
