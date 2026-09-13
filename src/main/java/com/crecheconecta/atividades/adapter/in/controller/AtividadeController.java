package com.crecheconecta.atividades.adapter.in.controller;

import com.crecheconecta.atividades.adapter.in.dto.AtividadeResponseDTO;
import com.crecheconecta.atividades.adapter.in.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.atividades.adapter.in.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.atividades.application.port.in.AtividadeUseCase;
import com.crecheconecta.atividades.domain.model.TipoAtividade;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/atividades")
public class AtividadeController {

    private final AtividadeUseCase atividadeUseCase;

    public AtividadeController(AtividadeUseCase atividadeUseCase) {
        this.atividadeUseCase = atividadeUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> criar(
            @RequestHeader(value = "turmaUUID", required = true) UUID turmaId,
            @Valid @RequestBody NovaAtividadeRequestDTO request
    ) {
        UUID atividadeId = atividadeUseCase.criar(
                new AtividadeUseCase.ComandoCriar(
                        turmaId,
                        request.professorId(),
                        request.tipo(),
                        request.titulo(),
                        request.descricao(),
                        request.prazoConclusao()
                )
        );

        URI location = URI.create("/api/atividades/" + atividadeId);
        return ResponseEntity.created(location).build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> atualizarParcial(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarAtividadeRequestDTO request
    ) {
        atividadeUseCase.atualizar(id, new AtividadeUseCase.ComandoAtualizar(
                request.tipo(),
                request.titulo(),
                request.descricao(),
                request.prazoConclusao()
        ));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        atividadeUseCase.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<AtividadeResponseDTO>> listarTodas(
            @RequestHeader(value = "turmaUUID", required = false) UUID turmaId,
            @RequestParam(value = "tipo", required = false) TipoAtividade tipo
    ) {
        List<AtividadeResponseDTO> atividades = atividadeUseCase.listar(turmaId, tipo)
                .stream()
                .map(AtividadeResponseDTO::fromDomain)
                .toList();
        return ResponseEntity.ok(atividades);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AtividadeResponseDTO> buscarPorId(@PathVariable UUID id) {
        AtividadeResponseDTO atividade = AtividadeResponseDTO.fromDomain(atividadeUseCase.buscarPorId(id));
        return ResponseEntity.ok(atividade);
    }
}