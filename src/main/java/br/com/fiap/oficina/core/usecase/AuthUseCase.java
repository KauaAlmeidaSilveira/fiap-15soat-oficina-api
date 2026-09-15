package br.com.fiap.oficina.core.usecase;

import br.com.fiap.oficina.core.domain.entity.Usuario;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;

import java.util.Locale;
import java.util.Set;

public class AuthUseCase {

    private static final String PERFIL_PADRAO = "OPERADOR";

    private final UsuarioGateway usuarioGateway;
    private final CriptografiaSenhaGateway senhaGateway;

    public AuthUseCase(UsuarioGateway usuarioGateway, CriptografiaSenhaGateway senhaGateway) {
        this.usuarioGateway = usuarioGateway;
        this.senhaGateway = senhaGateway;
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
}
