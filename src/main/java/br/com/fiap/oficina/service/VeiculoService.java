package br.com.fiap.oficina.service;

import br.com.fiap.oficina.dataprovider.persistence.entity.Veiculo;
import br.com.fiap.oficina.dto.response.VeiculoResponse;
import org.springframework.stereotype.Service;

@Service
public class VeiculoService {

    public VeiculoResponse toResponse(Veiculo v) {
        return new VeiculoResponse(v.getId(), v.getPlaca(), v.getMarca(),
                v.getModelo(), v.getAno(), v.getCor(), v.getChassi(), v.getCriadoEm());
    }
}
