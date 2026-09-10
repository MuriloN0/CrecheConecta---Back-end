package com.crecheconecta.atividades.application.service;

import com.crecheconecta.atividades.application.port.in.CriarAtividadeUseCase;
import com.crecheconecta.atividades.application.port.out.AtividadeRepository;
import com.crecheconecta.atividades.domain.model.Atividade;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AtividadeService implements CriarAtividadeUseCase {

    private final AtividadeRepository repository;

    public AtividadeService(AtividadeRepository repository) {
        this.repository = repository;
    }

    @Override
    public UUID executar(Comando comando) {
        UUID novoId = UUID.randomUUID();

        Atividade atividade = new Atividade(
                novoId,
                comando.turmaId(),
                comando.professorId(),
                comando.tipo(),
                comando.titulo(),
                comando.descricao(),
                Instant.now(),
                comando.prazoConclusao()
        );

        repository.salvar(atividade);

        return novoId;
    }
}
