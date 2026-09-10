package com.crecheconecta.saude.application.port.in;

import com.crecheconecta.saude.domain.model.FichaSaude;

import java.util.List;
import java.util.UUID;

public interface ListarFichasSaudeUseCase {
    List<FichaSaude> executar(Consulta consulta);

    record Consulta(UUID usuarioId, UUID alunoId) {}
}
