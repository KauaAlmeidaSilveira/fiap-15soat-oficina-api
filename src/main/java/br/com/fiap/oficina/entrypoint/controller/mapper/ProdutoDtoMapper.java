package br.com.fiap.oficina.entrypoint.controller.mapper;

import br.com.fiap.oficina.core.domain.entity.MovimentacaoEstoque;
import br.com.fiap.oficina.core.domain.entity.Produto;
import br.com.fiap.oficina.dto.request.ProdutoRequest;
import br.com.fiap.oficina.dto.response.MovimentacaoEstoqueResponse;
import br.com.fiap.oficina.dto.response.ProdutoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProdutoDtoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "saldoEstoque", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    Produto toDomain(ProdutoRequest request);

    ProdutoResponse toResponse(Produto produto);

    MovimentacaoEstoqueResponse toMovResponse(MovimentacaoEstoque movimentacao);
}
