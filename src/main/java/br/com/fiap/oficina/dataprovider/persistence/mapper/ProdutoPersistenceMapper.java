package br.com.fiap.oficina.dataprovider.persistence.mapper;

import br.com.fiap.oficina.core.domain.entity.Produto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProdutoPersistenceMapper {

    @Mapping(target = "saldoEstoque", source = "saldoEstoque.quantidade")
    Produto toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.Produto entity);

    @Mapping(target = "saldoEstoque", ignore = true)
    @Mapping(target = "movimentacoes", ignore = true)
    br.com.fiap.oficina.dataprovider.persistence.entity.Produto toEntity(Produto domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "saldoEstoque", ignore = true)
    @Mapping(target = "movimentacoes", ignore = true)
    void updateEntity(@MappingTarget br.com.fiap.oficina.dataprovider.persistence.entity.Produto entity, Produto domain);
}
