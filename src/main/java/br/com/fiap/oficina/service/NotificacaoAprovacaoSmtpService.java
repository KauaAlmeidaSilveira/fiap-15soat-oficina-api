package br.com.fiap.oficina.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("default")
@RequiredArgsConstructor
public class NotificacaoAprovacaoSmtpService implements NotificacaoAprovacaoService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String remetente;

    @Override
    @Async
    public void notificar(Dados dados, String linkAprovar, String linkRecusar) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(dados.clienteEmail());
            helper.setSubject(AprovacaoEmailTemplate.assunto(dados));
            helper.setText(
                    AprovacaoEmailTemplate.corpo(dados, linkAprovar, linkRecusar),
                    AprovacaoEmailTemplate.corpoHtml(dados, linkAprovar, linkRecusar));

            mailSender.send(mimeMessage);
            log.info("E-mail de aprovação enviado para {} (OS {})", dados.clienteEmail(), dados.numeroOS());
        } catch (MessagingException e) {
            log.warn("Falha ao montar e-mail de aprovação para {}: {}", dados.clienteEmail(), e.getMessage());
        }
    }
}
