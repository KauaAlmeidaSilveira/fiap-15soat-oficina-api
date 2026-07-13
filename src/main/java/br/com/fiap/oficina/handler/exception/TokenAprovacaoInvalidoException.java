package br.com.fiap.oficina.handler.exception;

public class TokenAprovacaoInvalidoException extends RuntimeException {
    public TokenAprovacaoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
