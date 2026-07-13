package br.com.fiap.oficina.entrypoint.controller.mapper;

import br.com.fiap.oficina.core.domain.entity.Cliente;
import br.com.fiap.oficina.dto.request.ClienteRequest;
import br.com.fiap.oficina.dto.response.ClienteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClienteDtoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    Cliente toDomain(ClienteRequest request);

    ClienteResponse toResponse(Cliente cliente);
}
