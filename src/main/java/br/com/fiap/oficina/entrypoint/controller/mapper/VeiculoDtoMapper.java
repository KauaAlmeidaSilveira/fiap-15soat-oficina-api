package br.com.fiap.oficina.entrypoint.controller.mapper;

import br.com.fiap.oficina.core.domain.entity.Veiculo;
import br.com.fiap.oficina.dto.request.VeiculoRequest;
import br.com.fiap.oficina.dto.response.VeiculoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VeiculoDtoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    Veiculo toDomain(VeiculoRequest request);

    VeiculoResponse toResponse(Veiculo veiculo);
}
