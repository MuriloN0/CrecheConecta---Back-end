package com.crecheconecta.escolar.adapter.in.web;

import com.crecheconecta.escolar.application.model.Pagina;
import com.crecheconecta.escolar.application.model.ResumoAluno;
import com.crecheconecta.escolar.application.port.in.GerenciarAlunoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/alunos")
public class AlunoController {
    private final GerenciarAlunoUseCase useCase;

    public AlunoController(GerenciarAlunoUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<AlunoResponse> cadastrar(
            @Valid @RequestBody AlunoRequest request
    ) {
        var aluno = useCase.cadastrar(request.paraDominio());

        return ResponseEntity
                .created(URI.create("/api/alunos/" + aluno.id()))
                .body(AlunoResponse.de(aluno));
    }

    @GetMapping("/{id}")
    public AlunoResponse buscar(@PathVariable UUID id) {
        return AlunoResponse.de(useCase.buscar(id));
    }

    @GetMapping
    public Pagina<ResumoAluno> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return useCase.listar(pagina, tamanho);
    }

    @PutMapping("/{id}")
    public AlunoResponse atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarAlunoRequest request
    ) {
        var aluno = useCase.atualizar(
                id,
                request.versao(),
                request.dados().paraDominio()
        );

        return AlunoResponse.de(aluno);
    }

    @PatchMapping("/{id}/inativacao")
    public ResponseEntity<Void> inativar(
            @PathVariable UUID id,
            @Valid @RequestBody InativarAlunoRequest request
    ) {
        useCase.inativar(id, request.versao());

        return ResponseEntity.noContent().build();
    }
}
