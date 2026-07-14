package br.com.fiap.oficina.dataprovider.notificacao;

import br.com.fiap.oficina.core.gateway.NotificacaoAprovacaoGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"test", "dev"})
public class NotificacaoAprovacaoLogService implements NotificacaoAprovacaoGateway {

    @Override
    public void notificar(Dados dados, String linkAprovar, String linkRecusar) {
        log.info("--- E-MAIL SIMULADO ---\nPara: {} <{}>\nAssunto: {}\n\n{}",
                dados.clienteNome(), dados.clienteEmail(), AprovacaoEmailTemplate.assunto(dados),
                AprovacaoEmailTemplate.corpo(dados, linkAprovar, linkRecusar));
    }
}
