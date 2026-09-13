package com.crecheconecta.saude.application.port.in;

import java.util.List;
import java.util.UUID;

public interface CadastrarFichaSaudeUseCase {
    UUID executar (Comando comando);

    record Comando(
            UUID usuarioId,
            UUID alunoId,
            String nome,
            String observacoes,
            List<ArquivoUpload> arquivos
    ) {}

    record ArquivoUpload(
            String nomeArquivo,
            String tipoConteudo,
            byte[] conteudo
    ) {}
}
