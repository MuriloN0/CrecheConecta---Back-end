package com.crecheconecta.saude.application.port.in;

import com.crecheconecta.saude.domain.model.FichaSaude;

import java.util.UUID;

public interface VisualizarFichaSaudeUseCase {
    FichaSaude executar(Consulta consulta);

    record Consulta(UUID usuarioId, UUID alunoId, UUID fichaId) {}
}
