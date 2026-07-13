package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OsItemDomainUnitTest {

    @Test
    @DisplayName("subtotal deve ser quantidade * preço unitário")
    void subtotalDeveSerQuantidadeVezesPreco() {
        OsItem item = OsItem.builder()
                .produtoTipo(TipoProduto.PECA)
                .quantidade(4)
                .precoUnitario(new BigDecimal("120.00"))
                .build();

        assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("480.00"));
    }

    @Test
    @DisplayName("subtotal deve ser zero quando quantidade ou preço são nulos")
    void subtotalDeveSerZeroQuandoNulo() {
        assertThat(OsItem.builder().quantidade(2).build().subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(OsItem.builder().precoUnitario(BigDecimal.TEN).build().subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
