package com.crecheconecta.controller;

import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.dto.AtividadeTurmaResponse;
import com.crecheconecta.dto.AtualizarAtividadeRequestDTO;
import com.crecheconecta.dto.NovaAtividadeRequestDTO;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.service.AtividadeService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/turmas")
    public ResponseEntity<List<AtividadeTurmaResponse>> listarTurmas() {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(atividadeService.listarTurmas());
    }

    @PostMapping
    public ResponseEntity<Void> criar(@RequestHeader("turmaUUID") UUID turmaId, @Valid @RequestBody NovaAtividadeRequestDTO request) {

        UUID atividadeId = atividadeService.criar(turmaId, request);

        return ResponseEntity.created(URI.create("/api/atividades/" + atividadeId)).cacheControl(CacheControl.noStore()).build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> atualizarParcial(@PathVariable UUID id, @Valid @RequestBody AtualizarAtividadeRequestDTO request) {

        atividadeService.atualizar(id, request);

        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {

        atividadeService.deletar(id);

        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    public ResponseEntity<List<AtividadeResponseDTO>> listarTodas(@RequestHeader(value = "turmaUUID", required = false) UUID turmaId, @RequestParam(value = "tipo", required = false) TipoAtividade tipo) {

        var resposta = atividadeService.listar(turmaId, tipo)
                .stream()
                .map(AtividadeResponseDTO::fromEntity)
                .toList();

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AtividadeResponseDTO> buscarPorId(@PathVariable UUID id) {

        var resposta = AtividadeResponseDTO.fromEntity(atividadeService.buscarPorId(id));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}