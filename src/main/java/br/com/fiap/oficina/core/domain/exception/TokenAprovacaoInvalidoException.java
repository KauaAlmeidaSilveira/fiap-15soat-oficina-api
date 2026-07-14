package br.com.fiap.oficina.core.domain.exception;

public class TokenAprovacaoInvalidoException extends RuntimeException {
    public TokenAprovacaoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
