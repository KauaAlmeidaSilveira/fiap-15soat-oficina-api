package br.com.fiap.oficina.core.gateway;

public interface TokenAprovacaoGateway {

    String gerarToken(Long osId, boolean aprovado);

    TokenAprovacao validar(String token);

    record TokenAprovacao(Long osId, boolean aprovado) {}
}
