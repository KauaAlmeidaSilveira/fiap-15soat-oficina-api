package br.com.fiap.oficina.config;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import br.com.fiap.oficina.core.domain.enums.TipoMovimentacao;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dataprovider.persistence.entity.ClienteVeiculo;
import br.com.fiap.oficina.dataprovider.persistence.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico;
import br.com.fiap.oficina.dataprovider.persistence.entity.OsItem;
import br.com.fiap.oficina.dataprovider.persistence.entity.Produto;
import br.com.fiap.oficina.dataprovider.persistence.entity.Role;
import br.com.fiap.oficina.dataprovider.persistence.entity.SaldoEstoque;
import br.com.fiap.oficina.dataprovider.persistence.entity.User;
import br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ClienteVeiculoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.MovimentacaoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.OrdemServicoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.RoleRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.SaldoEstoqueRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.UserRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataLoader {

    @Bean
    @Profile("!test")
    CommandLineRunner loadData(
            ClienteRepository clienteRepo,
            VeiculoRepository veiculoRepo,
            ClienteVeiculoRepository cvRepo,
            ProdutoRepository produtoRepo,
            SaldoEstoqueRepository saldoRepo,
            MovimentacaoEstoqueRepository movRepo,
            OrdemServicoRepository osRepo,
            UserRepository userRepo,
            RoleRepository roleRepo) {

        return args -> {
            if (clienteRepo.count() > 0) {
                log.info("=== Dados já existem no banco — DataLoader ignorado ===");
                return;
            }
            log.info("=== Carregando dados iniciais de demonstração ===");

            // --- Roles ---
            Role roleAdmin = new Role();
            roleAdmin.setName(Role.Values.ADMIN.name());
            roleAdmin = roleRepo.save(roleAdmin);

            Role roleOperador = new Role();
            roleOperador.setName(Role.Values.OPERADOR.name());
            roleOperador = roleRepo.save(roleOperador);

            Role roleRecepcao = new Role();
            roleRecepcao.setName(Role.Values.RECEPCAO.name());
            roleRecepcao = roleRepo.save(roleRecepcao);

            // --- Usuário admin (dono / gerente) ---
            User admin = new User();
            admin.setUsername("kaua@gmail.com");
            admin.setPassword(new BCryptPasswordEncoder().encode("Admin@123"));
            admin.setRoles(Set.of(roleAdmin));
            admin = userRepo.save(admin);

            // --- Usuário recepcionista de exemplo ---
            User recepcao = new User();
            recepcao.setUsername("recepcao@oficina.com");
            recepcao.setPassword(new BCryptPasswordEncoder().encode("Recepcao@123"));
            recepcao.setRoles(Set.of(roleRecepcao));
            userRepo.save(recepcao);

            // --- Usuário operador de exemplo ---
            User operador = new User();
            operador.setUsername("operador@oficina.com");
            operador.setPassword(new BCryptPasswordEncoder().encode("Operador@123"));
            operador.setRoles(Set.of(roleOperador));
            userRepo.save(operador);

            // --- Clientes ---
            Cliente joao = clienteRepo.save(Cliente.builder()
                    .nome("João da Silva").cpfCnpj("12345678901")
                    .tipoDocumento(TipoDocumento.CPF)
                    .telefone("11999990001")
                    .email("joao@email.com")
                    .endereco("Rua das Flores, 100 - SP").build());

            Cliente maria = clienteRepo.save(Cliente.builder()
                    .nome("Maria Oliveira").cpfCnpj("98765432100")
                    .tipoDocumento(TipoDocumento.CPF)
                    .telefone("11999990002")
                    .email("maria@email.com")
                    .endereco("Av. Paulista, 200 - SP").build());

            Cliente empresa = clienteRepo.save(Cliente.builder()
                    .nome("Transportes Rápidos Ltda").cpfCnpj("12345678000199")
                    .tipoDocumento(TipoDocumento.CNPJ)
                    .telefone("11333330001")
                    .email("frota@transportes.com")
                    .endereco("Rod. Anchieta, km 10 - SP").build());

            // --- Veículos ---
            Veiculo corolla = veiculoRepo.save(Veiculo.builder()
                    .placa("ABC1234").marca("Toyota")
                    .modelo("Corolla")
                    .ano(2021).cor("Prata").build());

            Veiculo hb20 = veiculoRepo.save(Veiculo.builder()
                    .placa("DEF5678").marca("Hyundai")
                    .modelo("HB20")
                    .ano(2019).cor("Branco").build());

            Veiculo sprinter = veiculoRepo.save(Veiculo.builder()
                    .placa("XYZ9A01").marca("Mercedes-Benz")
                    .modelo("Sprinter")
                    .ano(2022).cor("Cinza").build());

            // --- Vínculo Cliente-Veículo ---
            cvRepo.save(ClienteVeiculo.builder().cliente(joao).veiculo(corolla).build());
            cvRepo.save(ClienteVeiculo.builder().cliente(maria).veiculo(hb20).build());
            cvRepo.save(ClienteVeiculo.builder().cliente(empresa).veiculo(sprinter).build());

            // --- Produtos (Peças) ---
            Produto filtroOleo = criarPeca(produtoRepo, saldoRepo, movRepo,
                    "Filtro de Óleo", "Filtro original 1L", "45.00", "UN", 50);
            criarPeca(produtoRepo, saldoRepo, movRepo,
                    "Filtro de Ar", "Filtro de ar do motor", "35.00", "UN", 30);
            Produto oleoMotor = criarPeca(produtoRepo, saldoRepo, movRepo,
                    "Óleo Motor 5W30", "Óleo sintético 5W30 - 1L", "28.00", "L", 100);
            criarPeca(produtoRepo, saldoRepo, movRepo,
                    "Pastilha de Freio Dianteira", "Kit com 4 pastilhas", "120.00", "KIT", 20);
            criarPeca(produtoRepo, saldoRepo, movRepo,
                    "Correia Dentada", "Correia dentada + tensor", "180.00", "KIT", 15);

            // --- Produtos (Serviços) ---
            Produto trocaOleo = criarServico(produtoRepo,
                    "Troca de Óleo e Filtro", "Mão de obra para troca de óleo", "80.00");
            criarServico(produtoRepo,
                    "Alinhamento e Balanceamento", "Alinhamento 4 rodas + balanceamento", "150.00");
            criarServico(produtoRepo,
                    "Revisão de Freios", "Inspeção e substituição de pastilhas/discos", "120.00");
            criarServico(produtoRepo,
                    "Troca de Correia Dentada", "Mão de obra para troca de correia", "200.00");

            // --- Ordens de Serviço de exemplo ---
            // OS 1 - Aguardando aprovação
            OrdemServico os1 = OrdemServico.builder()
                    .cliente(joao).veiculo(corolla)
                    .numero("OS" + System.currentTimeMillis())
                    .status(StatusOS.AGUARDANDO_APROVACAO)
                    .descricaoProblema("Troca de óleo e revisão dos freios")
                    .user(admin)
                    .build();
            os1.getItens().addAll(List.of(
                    OsItem.builder().ordemServico(os1).produto(trocaOleo)
                            .quantidade(1).precoUnitario(trocaOleo.getPrecoUnitario()).build(),
                    OsItem.builder().ordemServico(os1).produto(oleoMotor)
                            .quantidade(4).precoUnitario(oleoMotor.getPrecoUnitario()).build(),
                    OsItem.builder().ordemServico(os1).produto(filtroOleo)
                            .quantidade(1).precoUnitario(filtroOleo.getPrecoUnitario()).build()
            ));
            BigDecimal totalOs1 = os1.getItens().stream()
                    .map(i -> BigDecimal.valueOf(i.getQuantidade()).multiply(i.getPrecoUnitario()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            os1.setValorTotal(totalOs1);
            osRepo.save(os1);

            // OS 3 - Recebida (nova)
            OrdemServico os3 = OrdemServico.builder()
                    .cliente(empresa).veiculo(sprinter)
                    .numero("OS" + (System.currentTimeMillis() + 1))
                    .status(StatusOS.RECEBIDA)
                    .descricaoProblema("Barulho ao frear - verificar freios dianteiros")
                    .user(admin)
                    .build();
            osRepo.save(os3);

            // OS 4 - Finalizada com datas para alimentar a métrica de tempo médio
            LocalDateTime inicio4 = LocalDateTime.now().minusDays(2);
            LocalDateTime fim4 = inicio4.plusHours(3).plusMinutes(30);
            OrdemServico os4 = OrdemServico.builder()
                    .cliente(joao).veiculo(corolla)
                    .numero("OS" + (System.currentTimeMillis() + 2))
                    .status(StatusOS.FINALIZADA)
                    .descricaoProblema("Troca de correia dentada e revisão geral")
                    .dataInicio(inicio4)
                    .dataFim(fim4)
                    .user(admin)
                    .build();
            BigDecimal totalOs4 = BigDecimal.valueOf(200.00);
            os4.setValorTotal(totalOs4);
            osRepo.save(os4);

            // OS 5 - Entregue com datas para enriquecer a métrica de tempo médio
            LocalDateTime inicio5 = LocalDateTime.now().minusDays(5);
            LocalDateTime fim5 = inicio5.plusHours(1).plusMinutes(45);
            OrdemServico os5 = OrdemServico.builder()
                    .cliente(maria).veiculo(hb20)
                    .numero("OS" + (System.currentTimeMillis() + 3))
                    .status(StatusOS.ENTREGUE)
                    .descricaoProblema("Alinhamento, balanceamento e troca de pastilhas")
                    .dataInicio(inicio5)
                    .dataFim(fim5)
                    .user(admin)
                    .build();
            BigDecimal totalOs5 = BigDecimal.valueOf(270.00);
            os5.setValorTotal(totalOs5);
            osRepo.save(os5);

            log.info("=== Dados carregados com sucesso ===");
            log.info("Clientes: {}", clienteRepo.count());
            log.info("Veículos: {}", veiculoRepo.count());
            log.info("Produtos: {}", produtoRepo.count());
            log.info("Ordens de Serviço: {}", osRepo.count());
            log.info("Swagger: http://localhost:8080/swagger-ui.html");
            log.info("H2 Console: http://localhost:8080/h2-console");
        };
    }

    private Produto criarPeca(ProdutoRepository prodRepo, SaldoEstoqueRepository saldoRepo,
                               MovimentacaoEstoqueRepository movRepo,
                               String nome, String desc, String preco, String unidade, int qtdInicial) {
        Produto p = prodRepo.save(Produto.builder()
                .nome(nome).descricao(desc).tipo(TipoProduto.PECA)
                .precoUnitario(new BigDecimal(preco)).unidadeMedida(unidade).ativo(true).build());
        saldoRepo.save(SaldoEstoque.builder().produto(p).quantidade(qtdInicial).build());
        movRepo.save(MovimentacaoEstoque.builder()
                .produto(p)
                .tipo(TipoMovimentacao.ENTRADA)
                .quantidade(qtdInicial)
                .motivo("Estoque inicial - carga do sistema")
                .build());
        return p;
    }

    private Produto criarServico(ProdutoRepository prodRepo, String nome, String desc, String preco) {
        return prodRepo.save(Produto.builder()
                .nome(nome).descricao(desc).tipo(TipoProduto.SERVICO)
                .precoUnitario(new BigDecimal(preco)).ativo(true).build());
    }
}
