package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Usuario;

import java.util.Optional;

public interface UsuarioGateway {

    Optional<Usuario> buscarPorUsername(String username);

    Usuario salvar(Usuario usuario);

    boolean existePorUsername(String username);
}
