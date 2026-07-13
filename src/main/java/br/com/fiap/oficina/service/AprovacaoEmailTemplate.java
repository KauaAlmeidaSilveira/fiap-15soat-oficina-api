package br.com.fiap.oficina.service;

final class AprovacaoEmailTemplate {

    private AprovacaoEmailTemplate() {}

    static String assunto(NotificacaoAprovacaoService.Dados dados) {
        return "Orçamento da OS " + dados.numeroOS() + " aguardando aprovação";
    }

    static String corpo(NotificacaoAprovacaoService.Dados dados, String linkAprovar, String linkRecusar) {
        StringBuilder corpo = new StringBuilder();
        corpo.append("Olá, ").append(dados.clienteNome()).append("!\n\n");
        corpo.append("O orçamento da sua Ordem de Serviço ").append(dados.numeroOS())
                .append(" está pronto para aprovação.\n\n");
        corpo.append("Veículo: ").append(dados.veiculoMarca()).append(" ")
                .append(dados.veiculoModelo()).append(" - placa ").append(dados.veiculoPlaca())
                .append("\n\n");
        corpo.append("Itens:\n");
        for (var item : dados.itens()) {
            corpo.append("- ").append(item.nomeProduto())
                    .append(" x").append(item.quantidade())
                    .append(" (R$ ").append(item.precoUnitario()).append(" cada)\n");
        }
        corpo.append("\nValor total: R$ ").append(dados.valorTotal()).append("\n\n");
        corpo.append("Aprovar orçamento: ").append(linkAprovar).append("\n");
        corpo.append("Recusar orçamento: ").append(linkRecusar).append("\n");
        return corpo.toString();
    }

    static String corpoHtml(NotificacaoAprovacaoService.Dados dados, String linkAprovar, String linkRecusar) {
        StringBuilder linhasItens = new StringBuilder();
        for (var item : dados.itens()) {
            linhasItens.append("""
                    <tr>
                      <td style="padding:6px 0;color:#374151;">%s x%d</td>
                      <td style="padding:6px 0;color:#374151;text-align:right;">R$ %s</td>
                    </tr>
                    """.formatted(item.nomeProduto(), item.quantidade(), item.precoUnitario()));
        }

        return """
                <!DOCTYPE html>
                <html lang="pt-br">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f4f5f7;font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:32px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;">
                        <tr><td style="background:#111827;padding:20px 32px;">
                          <span style="color:#ffffff;font-size:17px;font-weight:bold;">Oficina Mecânica</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="margin:0 0 16px;color:#1f2937;font-size:16px;">Olá, %s!</p>
                          <p style="margin:0 0 24px;color:#4b5563;font-size:14px;line-height:1.6;">
                            O orçamento da sua Ordem de Serviço <strong>%s</strong> está pronto para aprovação.
                          </p>
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;padding:16px;margin-bottom:24px;font-size:14px;">
                            <tr><td colspan="2" style="padding-bottom:8px;border-bottom:1px solid #e5e7eb;color:#111827;font-weight:bold;">Veículo</td></tr>
                            <tr><td colspan="2" style="padding-top:8px;color:#374151;">%s %s — placa %s</td></tr>
                            <tr><td colspan="2" style="padding-top:16px;padding-bottom:8px;border-bottom:1px solid #e5e7eb;color:#111827;font-weight:bold;">Itens</td></tr>
                            %s
                            <tr><td style="padding-top:12px;border-top:1px solid #e5e7eb;color:#111827;font-weight:bold;">Total</td>
                                <td style="padding-top:12px;border-top:1px solid #e5e7eb;color:#111827;font-weight:bold;text-align:right;">R$ %s</td></tr>
                          </table>
                          <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto;">
                            <tr>
                              <td style="padding-right:8px;">
                                <a href="%s" style="display:inline-block;background:#16a34a;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:14px;font-weight:bold;">Aprovar orçamento</a>
                              </td>
                              <td>
                                <a href="%s" style="display:inline-block;background:#dc2626;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:14px;font-weight:bold;">Recusar orçamento</a>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                dados.clienteNome(), dados.numeroOS(),
                dados.veiculoMarca(), dados.veiculoModelo(), dados.veiculoPlaca(),
                linhasItens, dados.valorTotal(),
                linkAprovar, linkRecusar);
    }
}
