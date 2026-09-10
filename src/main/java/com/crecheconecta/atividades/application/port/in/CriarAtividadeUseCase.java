package com.crecheconecta.atividades.application.port.in;

import com.crecheconecta.atividades.domain.model.TipoAtividade;
import java.time.LocalDate;
import java.util.UUID;

public interface CriarAtividadeUseCase {

    UUID executar(Comando comando);

    record Comando(
            UUID turmaId,
            UUID professorId,
            TipoAtividade tipo,
            String titulo,
            String descricao,
            LocalDate prazoConclusao
    ) {}
}