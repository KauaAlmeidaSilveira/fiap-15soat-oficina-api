package br.com.fiap.oficina.handler;

import br.com.fiap.oficina.handler.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.handler.exception.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Deve retornar 404 para RecursoNaoEncontradoException")
    void deveHandleNotFound() {
        RecursoNaoEncontradoException ex = new RecursoNaoEncontradoException("Cliente não encontrado");

        ProblemDetail pd = handler.handleNotFound(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Cliente não encontrado");
        assertThat(pd.getTitle()).isEqualTo("Recurso não encontrado");
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("Deve retornar 422 para RegraDeNegocioException")
    void deveHandleNegocio() {
        RegraDeNegocioException ex = new RegraDeNegocioException("CPF já cadastrado");

        ProblemDetail pd = handler.handleNegocio(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(pd.getDetail()).isEqualTo("CPF já cadastrado");
        assertThat(pd.getTitle()).isEqualTo("Regra de negócio violada");
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("Deve retornar 400 para MethodArgumentNotValidException com campos inválidos")
    void deveHandleValidation() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "nome", "não pode ser vazio"));
        bindingResult.addError(new FieldError("target", "cpfCnpj", "formato inválido"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail pd = handler.handleValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Erro de validação");
        assertThat(pd.getProperties()).containsKey("campos");
        @SuppressWarnings("unchecked")
        Map<String, String> campos = (Map<String, String>) pd.getProperties().get("campos");
        assertThat(campos).containsEntry("nome", "não pode ser vazio");
        assertThat(campos).containsEntry("cpfCnpj", "formato inválido");
    }

    @Test
    @DisplayName("Deve retornar 409 para IllegalStateException")
    void deveHandleIllegalState() {
        IllegalStateException ex = new IllegalStateException("Conflito de estado");

        ProblemDetail pd = handler.handleIllegalState(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("Conflito de estado");
        assertThat(pd.getTitle()).isEqualTo("Conflito de estado");
        assertThat(pd.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("Deve retornar 500 para Exception genérica")
    void deveHandleGeneric() {
        Exception ex = new Exception("Erro inesperado");

        ProblemDetail pd = handler.handleGeneric(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("Erro interno no servidor.");
        assertThat(pd.getProperties()).containsKey("timestamp");
    }
}
