package br.com.fiap.oficina.core.gateway;

import java.util.List;

public interface TokenAutenticacaoGateway {

    String gerarToken(String username, List<String> roles, long expiracaoSegundos);
}
