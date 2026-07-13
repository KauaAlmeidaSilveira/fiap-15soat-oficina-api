package br.com.fiap.oficina.service;

import br.com.fiap.oficina.dataprovider.persistence.entity.User;
import br.com.fiap.oficina.dataprovider.persistence.repository.UserRepository;
import br.com.fiap.oficina.dto.request.LoginRequest;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    public boolean isPasswordValid(LoginRequest loginRequest, String userPassword) {
        return passwordEncoder.matches(loginRequest.password(), userPassword);
    }

}
