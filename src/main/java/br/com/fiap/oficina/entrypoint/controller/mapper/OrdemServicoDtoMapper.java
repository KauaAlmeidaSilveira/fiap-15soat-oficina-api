package br.com.fiap.oficina.entrypoint.controller.mapper;

import br.com.fiap.oficina.core.domain.entity.OrdemServico;
import br.com.fiap.oficina.core.domain.entity.OsItem;
import br.com.fiap.oficina.dto.response.OrdemServicoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {ClienteDtoMapper.class, VeiculoDtoMapper.class})
public interface OrdemServicoDtoMapper {

    OrdemServicoResponse toResponse(OrdemServico ordemServico);

    @Mapping(target = "produtoTipo", expression = "java(item.getProdutoTipo().name())")
    @Mapping(target = "subtotal", expression = "java(item.subtotal())")
    OrdemServicoResponse.OsItemResponse toItemResponse(OsItem item);
}
