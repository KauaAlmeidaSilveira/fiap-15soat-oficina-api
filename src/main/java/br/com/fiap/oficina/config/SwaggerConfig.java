package br.com.fiap.oficina.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Oficina Mecanica API")
                        .description("""
                                Sistema Integrado de Atendimento e Execucao de Servicos - MVP

                                Todos os endpoints estao abertos (sem autenticacao nesta versao MVP).

                                Links uteis:
                                - H2 Console: http://localhost:8080/h2-console
                                  - JDBC URL: jdbc:h2:mem:oficina
                                  - User: sa | Password: (vazio)
                                """)
                        .version("1.0.0-MVP")
                        .contact(new Contact()
                                .name("FIAP SOAT - Grupo Oficina")
                                .email("soat@fiap.com.br")));
    }
}
