package br.com.fiap.oficina.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoAprovacaoSmtpServiceUnitTest {

    @Mock JavaMailSender mailSender;
    NotificacaoAprovacaoSmtpService service;

    private NotificacaoAprovacaoService.Dados dados;

    @BeforeEach
    void setup() {
        service = new NotificacaoAprovacaoSmtpService(mailSender);
        ReflectionTestUtils.setField(service, "remetente", "oficina@gmail.com");

        Session session = Session.getDefaultInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));

        dados = new NotificacaoAprovacaoService.Dados(
                "OS123", "Carlos", "carlos@email.com", "VW", "Gol", "XYZ1234",
                List.of(new NotificacaoAprovacaoService.Dados.Item("Óleo", 2, new BigDecimal("50.00"))),
                BigDecimal.TEN);
    }

    @Test
    @DisplayName("Deve montar e enviar o e-mail (texto + HTML) via JavaMailSender")
    void deveEnviarEmailComDadosCorretos() throws Exception {
        service.notificar(dados, "http://localhost:8080/aprovacao-os?token=aprovar",
                "http://localhost:8080/aprovacao-os?token=recusar");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage enviado = captor.getValue();
        enviado.saveChanges(); // normalmente feito pelo JavaMailSenderImpl real antes do envio

        assertThat(enviado.getSubject()).contains("OS123");
        assertThat(enviado.getAllRecipients()[0].toString()).isEqualTo("carlos@email.com");
        assertThat(enviado.getFrom()[0].toString()).isEqualTo("oficina@gmail.com");
        assertThat(enviado.getContentType()).contains("multipart");

        // Estrutura gerada pelo MimeMessageHelper: multipart/mixed > multipart/related >
        // multipart/alternative > [texto puro, HTML]
        jakarta.mail.Multipart mixed = (jakarta.mail.Multipart) enviado.getContent();
        jakarta.mail.Multipart related = (jakarta.mail.Multipart) mixed.getBodyPart(0).getContent();
        jakarta.mail.Multipart alternativa = (jakarta.mail.Multipart) related.getBodyPart(0).getContent();
        String conteudoBruto = (String) alternativa.getBodyPart(0).getContent()
                + (String) alternativa.getBodyPart(1).getContent();
        assertThat(conteudoBruto)
                .contains("http://localhost:8080/aprovacao-os?token=aprovar")
                .contains("http://localhost:8080/aprovacao-os?token=recusar")
                .contains("Óleo");
    }
}
