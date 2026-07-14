package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.StatusOS;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemServicoDomainUnitTest {

    private OrdemServico novaOs() {
        return OrdemServico.builder().numero("OS1").status(StatusOS.RECEBIDA).build();
    }

    private OsItem item(TipoProduto tipo, int qtd, String preco) {
        return OsItem.builder().produtoTipo(tipo).quantidade(qtd).precoUnitario(new BigDecimal(preco)).build();
    }

    @Test
    @DisplayName("Deve avançar status sequencialmente até ENTREGUE")
    void deveAvancarStatusSequencial() {
        OrdemServico os = novaOs();
        os.avancarStatus();
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        os.avancarStatus();
        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        os.avancarStatus();
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getDataInicio()).isNotNull();
        assertThat(os.getDataAprovacao()).isNotNull();
        os.avancarStatus();
        assertThat(os.getStatus()).isEqualTo(StatusOS.FINALIZADA);
        assertThat(os.getDataFim()).isNotNull();
        os.avancarStatus();
        assertThat(os.getStatus()).isEqualTo(StatusOS.ENTREGUE);
        assertThat(os.getDataEntrega()).isNotNull();
    }

    @Test
    @DisplayName("Deve lançar exceção ao avançar OS já entregue")
    void deveLancarExcecaoAoAvancarEntregue() {
        OrdemServico os = OrdemServico.builder().status(StatusOS.ENTREGUE).build();
        assertThatThrownBy(os::avancarStatus)
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("entregue");
    }

    @Test
    @DisplayName("Deve lançar exceção ao avançar OS reprovada")
    void deveLancarExcecaoAoAvancarReprovada() {
        OrdemServico os = OrdemServico.builder().status(StatusOS.REPROVADA).build();
        assertThatThrownBy(os::avancarStatus)
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("reprovada");
    }

    @Test
    @DisplayName("Aprovar deve mover para EM_EXECUCAO e registrar datas")
    void aprovarDeveMoverParaExecucao() {
        OrdemServico os = OrdemServico.builder().status(StatusOS.AGUARDANDO_APROVACAO).build();
        os.aprovar(true);
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getDataAprovacao()).isNotNull();
        assertThat(os.getDataInicio()).isNotNull();
    }

    @Test
    @DisplayName("Reprovar deve mover para REPROVADA")
    void reprovarDeveMoverParaReprovada() {
        OrdemServico os = OrdemServico.builder().status(StatusOS.AGUARDANDO_APROVACAO).build();
        os.aprovar(false);
        assertThat(os.getStatus()).isEqualTo(StatusOS.REPROVADA);
    }

    @Test
    @DisplayName("Deve lançar exceção ao aprovar fora de AGUARDANDO_APROVACAO")
    void deveLancarExcecaoAprovarStatusErrado() {
        OrdemServico os = novaOs();
        assertThatThrownBy(() -> os.aprovar(true))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("AGUARDANDO_APROVACAO");
    }

    @Test
    @DisplayName("Adicionar item deve recalcular o total")
    void adicionarItemDeveRecalcularTotal() {
        OrdemServico os = novaOs();
        os.adicionarItem(item(TipoProduto.PECA, 2, "50.00"));
        os.adicionarItem(item(TipoProduto.PECA, 1, "200.00"));
        assertThat(os.getValorTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("Deve lançar exceção ao adicionar item em OS finalizada")
    void deveLancarExcecaoAdicionarItemFinalizada() {
        OrdemServico os = OrdemServico.builder().status(StatusOS.FINALIZADA).build();
        assertThatThrownBy(() -> os.adicionarItem(item(TipoProduto.PECA, 1, "10.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("finalizada");
    }

    @Test
    @DisplayName("Remover item deve recalcular o total")
    void removerItemDeveRecalcularTotal() {
        OrdemServico os = novaOs();
        OsItem a = item(TipoProduto.PECA, 2, "50.00");
        OsItem b = item(TipoProduto.PECA, 1, "200.00");
        os.adicionarItem(a);
        os.adicionarItem(b);
        os.removerItem(a);
        assertThat(os.getItens()).containsExactly(b);
        assertThat(os.getValorTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
