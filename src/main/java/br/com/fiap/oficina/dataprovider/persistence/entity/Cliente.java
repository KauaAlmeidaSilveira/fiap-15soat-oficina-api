package br.com.fiap.oficina.dataprovider.persistence.entity;

import br.com.fiap.oficina.core.domain.enums.StatusCliente;
import br.com.fiap.oficina.core.domain.enums.TipoDocumento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "cliente")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 14)
    private String cpfCnpj;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(length = 20)
    private String telefone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String endereco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ATIVO'")
    private StatusCliente status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClienteVeiculo> veiculos;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL)
    private List<OrdemServico> ordensServico;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        if (status == null) {
            status = StatusCliente.ATIVO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
