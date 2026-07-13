package br.com.fiap.oficina.dataprovider.persistence.mapper;

import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {ClientePersistenceMapper.class, VeiculoPersistenceMapper.class})
public interface OrdemServicoPersistenceMapper {

    OrdemServico toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.OrdemServico entity);

    @Mapping(target = "produtoId", source = "produto.id")
    @Mapping(target = "produtoNome", source = "produto.nome")
    @Mapping(target = "produtoTipo", source = "produto.tipo")
    OsItem toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.OsItem item);
}
