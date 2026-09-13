package com.crecheconecta.saude.application.port.in;

import java.util.UUID;

public interface ExcluirFichaSaudeUseCase {
    void executar(Comando comando);

    record Comando(UUID usuarioId, UUID alunoId, UUID fichaId) {}
}
