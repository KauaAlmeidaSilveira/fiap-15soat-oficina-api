package br.com.fiap.oficina.dataprovider.persistence.mapper;

import br.com.fiap.oficina.core.domain.entity.MovimentacaoEstoque;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MovimentacaoEstoquePersistenceMapper {

    @Mapping(target = "produtoId", source = "produto.id")
    @Mapping(target = "produtoNome", source = "produto.nome")
    @Mapping(target = "ordemServicoId", source = "ordemServico.id")
    MovimentacaoEstoque toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.MovimentacaoEstoque entity);
}
