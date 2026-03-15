package br.com.fiap.oficina.dto.response;

import java.time.LocalDateTime;

public record VeiculoResponse(
    Long id,
    String placa,
    String marca,
    String modelo,
    Integer ano,
    String cor,
    String chassi,
    LocalDateTime criadoEm
) {}
