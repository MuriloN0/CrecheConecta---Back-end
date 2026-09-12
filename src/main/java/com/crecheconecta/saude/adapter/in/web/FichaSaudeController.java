package com.crecheconecta.saude.adapter.in.web;

import com.crecheconecta.saude.application.port.in.AtualizarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.CadastrarFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.CadastrarFichaSaudeUseCase.ArquivoUpload;
import com.crecheconecta.saude.application.port.in.ExcluirFichaSaudeUseCase;
import com.crecheconecta.saude.application.port.in.ListarFichasSaudeUseCase;
import com.crecheconecta.saude.application.port.in.VisualizarFichaSaudeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alunos/{alunoId}/fichas-saude")
public class FichaSaudeController {
    private final CadastrarFichaSaudeUseCase cadastrar;
    private final ListarFichasSaudeUseCase listar;
    private final VisualizarFichaSaudeUseCase visualizar;
    private final AtualizarFichaSaudeUseCase atualizar;
    private final ExcluirFichaSaudeUseCase excluir;

    public FichaSaudeController(
            CadastrarFichaSaudeUseCase cadastrar,
            ListarFichasSaudeUseCase listar,
            VisualizarFichaSaudeUseCase visualizar,
            AtualizarFichaSaudeUseCase atualizar,
            ExcluirFichaSaudeUseCase excluir
    ) {
        this.cadastrar = cadastrar;
        this.listar = listar;
        this.visualizar = visualizar;
        this.atualizar = atualizar;
        this.excluir = excluir;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> cadastrar(
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestPart("dados") CadastrarFichaSaudeRequest dados,
            @RequestPart(value = "arquivos", required = false) List<MultipartFile> arquivos
    ) {
        UUID id = cadastrar.executar(new CadastrarFichaSaudeUseCase.Comando(
                usuario.id(), alunoId, dados.nome(), dados.observacoes(),
                paraUploads(arquivos)));
        return ResponseEntity.created(
                URI.create("/api/alunos/" + alunoId + "/fichas-saude/" + id)).build();
    }

    @GetMapping
    public List<FichaSaudeResumoResponse> listar(
            @PathVariable UUID alunoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        return listar.executar(new ListarFichasSaudeUseCase.Consulta(usuario.id(), alunoId))
                .stream().map(FichaSaudeResumoResponse::de).toList();
    }

    @GetMapping("/{fichaId}")
    public FichaSaudeResponse visualizar(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        return FichaSaudeResponse.de(visualizar.executar(
                new VisualizarFichaSaudeUseCase.Consulta(usuario.id(), alunoId, fichaId)));
    }

    @PutMapping(path = "/{fichaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FichaSaudeResponse editar(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestPart("dados") AtualizarFichaSaudeRequest dados,
            @RequestPart(value = "arquivos", required = false) List<MultipartFile> novosArquivos
    ) {
        return FichaSaudeResponse.de(atualizar.executar(
                new AtualizarFichaSaudeUseCase.Comando(
                        usuario.id(), alunoId, fichaId, dados.nome(), dados.observacoes(),
                        dados.anexosMantidos(), paraUploads(novosArquivos))));
    }

    @DeleteMapping("/{fichaId}")
    public ResponseEntity<Void> excluir(
            @PathVariable UUID alunoId,
            @PathVariable UUID fichaId,
            @AuthenticationPrincipal UsuarioAutenticado usuario
    ) {
        excluir.executar(new ExcluirFichaSaudeUseCase.Comando(usuario.id(), alunoId, fichaId));
        return ResponseEntity.noContent().build();
    }

    private List<ArquivoUpload> paraUploads(List<MultipartFile> arquivos) {
        if (arquivos == null) {
            return List.of();
        }
        List<ArquivoUpload> uploads = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            try {
                uploads.add(new ArquivoUpload(
                        arquivo.getOriginalFilename(),
                        arquivo.getContentType(),
                        arquivo.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
            }
        }
        return uploads;
    }
}

