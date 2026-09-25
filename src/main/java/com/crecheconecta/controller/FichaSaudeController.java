package com.crecheconecta.controller;

import com.crecheconecta.dto.AtualizarFichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeRequest;
import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.security.Perfil;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.service.FichaSaudeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alunos/{alunoId}/fichas-saude")
public class FichaSaudeController {

    private final FichaSaudeService service;

    public FichaSaudeController(FichaSaudeService service) {
        this.service = service;
    }

    private UsuarioAtual usuarioFake() {
        return new UsuarioAtual(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), Perfil.DIRECAO);
    }

    // POST /api/alunos/{alunoId}/fichas-saude cadastra
    @PostMapping
    public ResponseEntity<Void> cadastrar(
            @PathVariable UUID alunoId, @Valid @RequestBody FichaSaudeRequest dto) {
        UUID id = service.cadastrar(alunoId, dto.paraDominio(), usuarioFake());
        return ResponseEntity.created(
                URI.create("/api/alunos/" + alunoId + "/fichas-saude/" + id)).build();
    }

    // GET  /api/alunos/{alunoId}/fichas-saude lista resumo
    @GetMapping
    public List<FichaSaudeResumoResponse> listar(@PathVariable UUID alunoId) {
        return service.listar(alunoId, usuarioFake());
    }

    // GET  /api/alunos/{alunoId}/fichas-saude/{fichaId} detalhe
    @GetMapping("/{fichaId}")
    public FichaSaudeResponse visualizar(
            @PathVariable UUID alunoId, @PathVariable UUID fichaId) {
        return service.visualizar(alunoId, fichaId, usuarioFake());
    }

    // PUT  /api/alunos/{alunoId}/fichas-saude/{fichaId}  edita
    @PutMapping("/{fichaId}")
    public FichaSaudeResponse atualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @Valid @RequestBody AtualizarFichaSaudeRequest dto) {
        return service.atualizar(alunoId, fichaId, dto.paraDominio(), dto.versao(), usuarioFake());
    }

    // DELETE /api/alunos/{alunoId}/fichas-saude/{fichaId}  excluir
    @DeleteMapping("/{fichaId}")
    public ResponseEntity<Void> excluir(
            @PathVariable UUID alunoId, @PathVariable UUID fichaId) {
        service.excluir(alunoId, fichaId, usuarioFake());
        return ResponseEntity.noContent().build();
    }
}
