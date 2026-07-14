package br.com.fiap.oficina.core.gateway;

import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;

import java.util.List;
import java.util.Optional;

public interface ProdutoGateway {

    Produto salvar(Produto produto);

    Optional<Produto> buscarPorId(Long id);

    List<Produto> listarTodos();

    List<Produto> listarPorTipo(TipoProduto tipo);
}
