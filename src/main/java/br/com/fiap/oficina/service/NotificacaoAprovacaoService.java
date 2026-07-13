package br.com.fiap.oficina.service;

import java.math.BigDecimal;
import java.util.List;

public interface NotificacaoAprovacaoService {

    void notificar(Dados dados, String linkAprovar, String linkRecusar);

    /**
     * Dados já materializados (não-JPA) para não vazar a Session do Hibernate para uma
     * eventual thread assíncrona — ver NotificacaoAprovacaoSmtpService.
     */
    record Dados(
            String numeroOS,
            String clienteNome,
            String clienteEmail,
            String veiculoMarca,
            String veiculoModelo,
            String veiculoPlaca,
            List<Item> itens,
            BigDecimal valorTotal
    ) {
        public record Item(String nomeProduto, Integer quantidade, BigDecimal precoUnitario) {}
    }
}
