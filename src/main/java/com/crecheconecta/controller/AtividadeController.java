package com.crecheconecta.controller;

import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.service.AtividadeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/atividades")
public class AtividadeController {

    private final AtividadeService atividadeService;

    public AtividadeController(AtividadeService atividadeService) {
        this.atividadeService = atividadeService;
    }

    @PostMapping
    public ResponseEntity<Void> criar(
            @RequestHeader(value = "turmaUUID", required = true) UUID turmaId,
            @Valid @RequestBody NovaAtividadeRequestDTO request
    ) {
        UUID atividadeId = atividadeService.criar(turmaId, request);
        URI location = URI.create("/api/atividades/" + atividadeId);
        return ResponseEntity.created(location).build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> atualizarParcial(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarAtividadeRequestDTO request
    ) {
        atividadeService.atualizar(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        atividadeService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<AtividadeResponseDTO>> listarTodas(
            @RequestHeader(value = "turmaUUID", required = false) UUID turmaId,
            @RequestParam(value = "tipo", required = false) TipoAtividade tipo
    ) {
        List<AtividadeResponseDTO> atividades = atividadeService.listar(turmaId, tipo)
                .stream()
                .map(AtividadeResponseDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(atividades);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AtividadeResponseDTO> buscarPorId(@PathVariable UUID id) {
        AtividadeResponseDTO atividade = AtividadeResponseDTO.fromEntity(atividadeService.buscarPorId(id));
        return ResponseEntity.ok(atividade);
    }
}