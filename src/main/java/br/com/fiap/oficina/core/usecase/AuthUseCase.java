package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Usuario;
import br.com.fiap.oficina.core.domain.exception.CredenciaisInvalidasException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.TokenAutenticacaoGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AuthUseCase {

    private static final long EXPIRACAO_SEGUNDOS = 28800L;
    private static final String PERFIL_PADRAO = "OPERADOR";

    private final UsuarioGateway usuarioGateway;
    private final TokenAutenticacaoGateway tokenGateway;
    private final CriptografiaSenhaGateway senhaGateway;

    public AuthUseCase(UsuarioGateway usuarioGateway, TokenAutenticacaoGateway tokenGateway,
                       CriptografiaSenhaGateway senhaGateway) {
        this.usuarioGateway = usuarioGateway;
        this.tokenGateway = tokenGateway;
        this.senhaGateway = senhaGateway;
    }

    public ResultadoLogin login(String username, String senha) {
        String normalizado = username.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioGateway.buscarPorUsername(normalizado)
                .orElseThrow(() -> new CredenciaisInvalidasException("Credenciais inválidas"));
        if (!senhaGateway.conferir(senha, usuario.getPassword())) {
            throw new CredenciaisInvalidasException("Credenciais inválidas");
        }
        String token = tokenGateway.gerarToken(usuario.getUsername(), List.copyOf(usuario.getRoles()), EXPIRACAO_SEGUNDOS);
        return new ResultadoLogin(token, EXPIRACAO_SEGUNDOS);
    }

    public Usuario register(String username, String senha) {
        String normalizado = username.trim().toLowerCase(Locale.ROOT);
        if (usuarioGateway.existePorUsername(normalizado)) {
            throw new RegraDeNegocioException("Usuário já cadastrado");
        }
        Usuario novo = Usuario.builder()
                .username(normalizado)
                .password(senhaGateway.codificar(senha))
                .roles(Set.of(PERFIL_PADRAO))
                .build();
        return usuarioGateway.salvar(novo);
    }

    public record ResultadoLogin(String token, long expiresIn) {}
}
