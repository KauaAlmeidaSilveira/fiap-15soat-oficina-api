package br.com.fiap.oficina.service;

import br.com.fiap.oficina.dataprovider.persistence.entity.Cliente;
import br.com.fiap.oficina.dto.response.ClienteResponse;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    public ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(), c.getCpfCnpj(),
                c.getTipoDocumento(), c.getTelefone(), c.getEmail(),
                c.getEndereco(), c.getCriadoEm());
    }
}
