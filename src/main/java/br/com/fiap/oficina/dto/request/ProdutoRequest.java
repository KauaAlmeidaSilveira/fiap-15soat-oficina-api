package br.com.fiap.oficina.dto.request;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProdutoRequest(

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100)
    String nome,

    @Size(max = 255)
    String descricao,

    @NotNull(message = "Tipo é obrigatório")
    TipoProduto tipo,

    @NotNull(message = "Preço unitário é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    BigDecimal precoUnitario,

    @Size(max = 20)
    String unidadeMedida,

    @Min(value = 0, message = "Quantidade inicial não pode ser negativa")
    Integer quantidadeInicial
) {}
