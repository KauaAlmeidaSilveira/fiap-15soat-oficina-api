package br.com.fiap.oficina.dataprovider.persistence.mapper;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ClientePersistenceMapper {

    Cliente toDomain(br.com.fiap.oficina.dataprovider.persistence.entity.Cliente entity);

    @Mapping(target = "veiculos", ignore = true)
    @Mapping(target = "ordensServico", ignore = true)
    br.com.fiap.oficina.dataprovider.persistence.entity.Cliente toEntity(Cliente domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "veiculos", ignore = true)
    @Mapping(target = "ordensServico", ignore = true)
    void updateEntity(@MappingTarget br.com.fiap.oficina.dataprovider.persistence.entity.Cliente entity, Cliente domain);
}
