package com.crecheconecta.saude.adapter.out.escolar;

import com.crecheconecta.saude.application.port.out.VinculoResponsavelPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VinculoResponsavelAdapter implements  VinculoResponsavelPort {

    @Override
    public boolean responsavelAtivoDoAluno(UUID usuarioId, UUID alunoId) {
        return usuarioId != null && alunoId != null;
    }
}
