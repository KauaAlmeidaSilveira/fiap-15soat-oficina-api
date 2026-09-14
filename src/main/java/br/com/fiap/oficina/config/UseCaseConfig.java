package br.com.fiap.oficina.config;

import br.com.fiap.oficina.core.gateway.ClienteGateway;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.EstoqueGateway;
import br.com.fiap.oficina.core.gateway.MetricasGateway;
import br.com.fiap.oficina.core.gateway.NotificacaoAprovacaoGateway;
import br.com.fiap.oficina.core.gateway.OrdemServicoGateway;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.core.gateway.TokenAprovacaoGateway;
import br.com.fiap.oficina.core.gateway.TokenAutenticacaoGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;
import br.com.fiap.oficina.core.gateway.VeiculoGateway;
import br.com.fiap.oficina.core.usecase.AuthUseCase;
import br.com.fiap.oficina.core.usecase.ClienteUseCase;
import br.com.fiap.oficina.core.usecase.OrdemServicoUseCase;
import br.com.fiap.oficina.core.usecase.ProdutoUseCase;
import br.com.fiap.oficina.core.usecase.VeiculoUseCase;
import org.springframework.beans.factory.annotation.Value;
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

    @Bean
    public ProdutoUseCase produtoUseCase(ProdutoGateway produtoGateway, EstoqueGateway estoqueGateway) {
        return new ProdutoUseCase(produtoGateway, estoqueGateway);
    }

    @Bean
    public OrdemServicoUseCase ordemServicoUseCase(
            OrdemServicoGateway ordemServicoGateway, ClienteGateway clienteGateway, VeiculoGateway veiculoGateway,
            ProdutoGateway produtoGateway, EstoqueGateway estoqueGateway, TokenAprovacaoGateway tokenAprovacaoGateway,
            NotificacaoAprovacaoGateway notificacaoAprovacaoGateway, MetricasGateway metricasGateway,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        return new OrdemServicoUseCase(ordemServicoGateway, clienteGateway, veiculoGateway, produtoGateway,
                estoqueGateway, tokenAprovacaoGateway, notificacaoAprovacaoGateway, metricasGateway, publicBaseUrl);
    }

    @Bean
    public AuthUseCase authUseCase(UsuarioGateway usuarioGateway, TokenAutenticacaoGateway tokenAutenticacaoGateway,
                                   CriptografiaSenhaGateway criptografiaSenhaGateway) {
        return new AuthUseCase(usuarioGateway, tokenAutenticacaoGateway, criptografiaSenhaGateway);
    }
}
