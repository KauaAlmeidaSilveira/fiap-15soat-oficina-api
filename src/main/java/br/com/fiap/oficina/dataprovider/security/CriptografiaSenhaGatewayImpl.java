package br.com.fiap.oficina.dataprovider.security;

import br.com.fiap.oficina.core.gateway.CriptografiaSenhaGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CriptografiaSenhaGatewayImpl implements CriptografiaSenhaGateway {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String codificar(String senhaPura) {
        return passwordEncoder.encode(senhaPura);
    }

    @Override
    public boolean conferir(String senhaPura, String senhaCodificada) {
        return passwordEncoder.matches(senhaPura, senhaCodificada);
    }
}
