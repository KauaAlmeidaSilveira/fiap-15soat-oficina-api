package br.com.fiap.oficina.controller;

final class PaginaConfirmacaoTemplate {

    private PaginaConfirmacaoTemplate() {}

    enum Tipo {
        SUCESSO("#16a34a", "#ecfdf5", "&#10003;"),
        ALERTA("#d97706", "#fffbeb", "!"),
        ERRO("#dc2626", "#fef2f2", "&times;");

        private final String cor;
        private final String corFundo;
        private final String icone;

        Tipo(String cor, String corFundo, String icone) {
            this.cor = cor;
            this.corFundo = corFundo;
            this.icone = icone;
        }
    }

    static String render(Tipo tipo, String titulo, String mensagem) {
        return """
                <!DOCTYPE html>
                <html lang="pt-br">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                  body {
                    font-family: -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
                    background: #f4f5f7;
                    margin: 0;
                    padding: 48px 20px;
                  }
                  .card {
                    max-width: 440px;
                    margin: 0 auto;
                    background: #ffffff;
                    border-radius: 14px;
                    padding: 40px 32px;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
                    text-align: center;
                  }
                  .icone {
                    width: 56px;
                    height: 56px;
                    line-height: 56px;
                    border-radius: 50%%;
                    background: %s;
                    color: %s;
                    font-size: 26px;
                    font-weight: bold;
                    margin: 0 auto 20px;
                  }
                  h1 {
                    font-size: 20px;
                    margin: 0 0 12px;
                    color: #1f2937;
                  }
                  p {
                    color: #6b7280;
                    font-size: 15px;
                    line-height: 1.6;
                    margin: 0;
                  }
                  .rodape {
                    margin-top: 28px;
                    font-size: 12px;
                    color: #9ca3af;
                  }
                </style>
                </head>
                <body>
                <div class="card">
                  <div class="icone">%s</div>
                  <h1>%s</h1>
                  <p>%s</p>
                  <div class="rodape">Oficina Mecânica</div>
                </div>
                </body>
                </html>
                """.formatted(titulo, tipo.corFundo, tipo.cor, tipo.icone, titulo, mensagem);
    }
}
