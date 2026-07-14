package br.com.fiap.oficina.dataprovider.persistence.mapper;

import br.com.fiap.oficina.core.domain.entity.Veiculo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface VeiculoPersistenceMapper {

    Veiculo toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo entity);

    @Mapping(target = "proprietarios", ignore = true)
    @Mapping(target = "ordensServico", ignore = true)
    br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo toEntity(Veiculo domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "proprietarios", ignore = true)
    @Mapping(target = "ordensServico", ignore = true)
    void updateEntity(@MappingTarget br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo entity, Veiculo domain);
}
