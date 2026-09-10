package com.crecheconecta.atividades.adapter.in.controller;

import com.crecheconecta.atividades.adapter.in.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.atividades.application.port.in.CriarAtividadeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/turmas/{turmaId}/atividades")
public class AtividadeController {

    private final CriarAtividadeUseCase useCase;

    public AtividadeController(CriarAtividadeUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<Void> criar(
            @PathVariable UUID turmaId,
            @Valid @RequestBody NovaAtividadeRequestDTO request
    ) {
        UUID atividadeId = useCase.executar(
                new CriarAtividadeUseCase.Comando(
                        turmaId,
                        request.professorId(),
                        request.tipo(),
                        request.titulo(),
                        request.descricao(),
                        request.prazoConclusao()
                )
        );

        URI location = URI.create("/api/turmas/" + turmaId + "/atividades/" + atividadeId);
        return ResponseEntity.created(location).build();
    }
}