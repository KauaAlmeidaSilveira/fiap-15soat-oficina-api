package br.com.fiap.oficina.service;

import br.com.fiap.oficina.domain.model.User;
import br.com.fiap.oficina.domain.repository.UserRepository;
import br.com.fiap.oficina.dto.request.LoginRequest;
import br.com.fiap.oficina.handler.exception.RecursoNaoEncontradoException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    public boolean isPasswordValid(LoginRequest loginRequest, String userPassword) {
        return new BCryptPasswordEncoder().matches(loginRequest.password(), userPassword);
    }

}
