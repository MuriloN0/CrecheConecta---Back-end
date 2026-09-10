package com.crecheconecta.saude.application.port.out;

import java.util.UUID;

public interface VinculoResponsavelPort {
    boolean responsavelAtivoDoAluno (UUID usuarioId, UUID alunoId);
}
