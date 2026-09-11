package com.crecheconecta.atividades.application.port.in;

import com.crecheconecta.atividades.domain.model.Atividade;
import com.crecheconecta.atividades.domain.model.TipoAtividade;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AtividadeUseCase {

    UUID criar(ComandoCriar comando);
    void atualizar(UUID id, ComandoAtualizar comando);
    void deletar(UUID id);
    Atividade buscarPorId(UUID id);
    List<Atividade> listar(UUID turmaId);

    record ComandoCriar(
            UUID turmaId,
            UUID professorId,
            TipoAtividade tipo,
            String titulo,
            String descricao,
            LocalDate prazoConclusao
    ) {}

    record ComandoAtualizar(
            TipoAtividade tipo,
            String titulo,
            String descricao,
            LocalDate prazoConclusao
    ) {}
}