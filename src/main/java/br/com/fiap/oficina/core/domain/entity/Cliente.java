package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    private Long id;
    private String nome;
    private String cpfCnpj;
    private TipoDocumento tipoDocumento;
    private String telefone;
    private String email;
    private String endereco;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
