package br.com.fiap.oficina.dataprovider.gateway;

import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.core.domain.enums.TipoProduto;
import br.com.fiap.oficina.core.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.oficina.core.gateway.ProdutoGateway;
import br.com.fiap.oficina.dataprovider.persistence.mapper.ProdutoPersistenceMapper;
import br.com.fiap.oficina.dataprovider.persistence.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProdutoGatewayImpl implements ProdutoGateway {

    private final ProdutoRepository produtoRepository;
    private final ProdutoPersistenceMapper mapper;

    @Override
    @Transactional
    public Produto salvar(Produto produto) {
        if (produto.getId() != null) {
            var existente = produtoRepository.findById(produto.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto", produto.getId()));
            mapper.updateEntity(existente, produto);
            return mapper.toDomain(produtoRepository.save(existente));
        }
        return mapper.toDomain(produtoRepository.save(mapper.toEntity(produto)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Produto> buscarPorId(Long id) {
        return produtoRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return produtoRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Produto> listarPorTipo(TipoProduto tipo) {
        return produtoRepository.findByTipo(tipo).stream().map(mapper::toDomain).toList();
    }
}
