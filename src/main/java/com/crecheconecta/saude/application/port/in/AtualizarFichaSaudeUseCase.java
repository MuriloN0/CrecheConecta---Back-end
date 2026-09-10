package com.crecheconecta.saude.application.port.in;

import com.crecheconecta.saude.domain.model.FichaSaude;

import java.util.List;
import java.util.UUID;

public interface AtualizarFichaSaudeUseCase {
    FichaSaude executar(Comando comando);

    record Comando(
            UUID usuarioId,
            UUID alunoId,
            UUID fichaId,
            String nome,
            String observacoes,
            List<UUID> anexosMantidos,
            List<CadastrarFichaSaudeUseCase.ArquivoUpload> novosArquivos
    ) {}
}
