package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Usuario;
import br.com.fiap.oficina.core.gateway.UsuarioGateway;
import br.com.fiap.oficina.dataprovider.persistence.entity.Role;
import br.com.fiap.oficina.dataprovider.persistence.entity.User;
import br.com.fiap.oficina.dataprovider.persistence.repository.RoleRepository;
import br.com.fiap.oficina.dataprovider.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UsuarioGatewayImpl implements UsuarioGateway {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorUsername(String username) {
        return userRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    @Transactional
    public Usuario salvar(Usuario usuario) {
        User entity = new User();
        entity.setUsername(usuario.getUsername());
        entity.setPassword(usuario.getPassword());
        entity.setRoles(resolverRoles(usuario.getRoles()));
        return toDomain(userRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    private Set<Role> resolverRoles(Set<String> nomes) {
        return nomes.stream()
                .map(nome -> roleRepository.findByName(nome)
                        .orElseThrow(() -> new IllegalStateException("Perfil " + nome + " não encontrado")))
                .collect(Collectors.toSet());
    }

    private Usuario toDomain(User user) {
        return Usuario.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }
}
