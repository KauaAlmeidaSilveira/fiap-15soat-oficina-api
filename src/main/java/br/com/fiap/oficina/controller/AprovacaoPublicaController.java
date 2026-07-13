package br.com.fiap.oficina.controller;

import br.com.fiap.oficina.dto.request.AprovarOsRequest;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import br.com.fiap.oficina.core.domain.exception.TokenAprovacaoInvalidoException;
import br.com.fiap.oficina.core.gateway.TokenAprovacaoGateway;
import br.com.fiap.oficina.service.OrdemServicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Aprovação por E-mail", description = "Endpoint público acionado pelos links do e-mail de aprovação de orçamento")
public class AprovacaoPublicaController {

    private final TokenAprovacaoGateway tokenService;
    private final OrdemServicoService osService;

    @GetMapping(value = "/aprovacao-os", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Processa a decisão do cliente (aprovar/recusar) a partir do link recebido por e-mail")
    public ResponseEntity<String> processar(@RequestParam String token) {
        try {
            var decodificado = tokenService.validar(token);
            osService.aprovar(decodificado.osId(), new AprovarOsRequest(decodificado.aprovado()));
            return ResponseEntity.ok(PaginaConfirmacaoTemplate.render(
                    PaginaConfirmacaoTemplate.Tipo.SUCESSO,
                    decodificado.aprovado() ? "Orçamento aprovado!" : "Orçamento recusado.",
                    decodificado.aprovado()
                            ? "Obrigado por aprovar. Sua ordem de serviço já entrou em execução."
                            : "Sua decisão foi registrada. A ordem de serviço não terá continuidade."));
        } catch (TokenAprovacaoInvalidoException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaginaConfirmacaoTemplate.render(PaginaConfirmacaoTemplate.Tipo.ERRO, "Link inválido", e.getMessage()));
        } catch (RecursoNaoEncontradoException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(PaginaConfirmacaoTemplate.render(PaginaConfirmacaoTemplate.Tipo.ERRO,
                            "Ordem de serviço não encontrada", "Verifique se o link está correto."));
        } catch (RegraDeNegocioException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(PaginaConfirmacaoTemplate.render(PaginaConfirmacaoTemplate.Tipo.ALERTA,
                            "Orçamento já processado", "Este orçamento já foi aprovado ou recusado anteriormente."));
        }
    }
}
