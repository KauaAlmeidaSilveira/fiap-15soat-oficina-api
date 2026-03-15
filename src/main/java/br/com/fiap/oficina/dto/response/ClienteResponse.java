package br.com.fiap.oficina.dto.response;

import br.com.fiap.oficina.domain.enums.TipoDocumento;

import java.time.LocalDateTime;

public record ClienteResponse(
    Long id,
    String nome,
    String cpfCnpj,
    TipoDocumento tipoDocumento,
    String telefone,
    String email,
    String endereco,
    LocalDateTime criadoEm
) {}
