package br.com.fiap.oficina.core.gateway;

public interface CriptografiaSenhaGateway {

    String codificar(String senhaPura);

    boolean conferir(String senhaPura, String senhaCodificada);
}
