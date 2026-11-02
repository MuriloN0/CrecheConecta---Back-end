package com.crecheconecta.controller;

import com.crecheconecta.dto.DirecaoResponse;
import com.crecheconecta.dto.InativarDirecaoRequest;
import com.crecheconecta.security.UsuarioAtual;
import com.crecheconecta.security.UsuarioAutenticado;
import com.crecheconecta.service.DirecaoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.crecheconecta.dto.ConvidarDirecaoRequest;
import com.crecheconecta.dto.ConviteCadastroResponse;
import com.crecheconecta.service.CadastroDirecaoService;
import org.springframework.http.HttpStatus;
import com.crecheconecta.dto.AtualizarDirecaoRequest;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/direcao")
@PreAuthorize("hasRole('DIRECAO')")
public class DirecaoController {

    private final DirecaoService direcao;
    private final CadastroDirecaoService cadastros;

    public DirecaoController(DirecaoService direcao, CadastroDirecaoService cadastros) {

        this.direcao = direcao;
        this.cadastros = cadastros;
    }

    @GetMapping("/me")
    public ResponseEntity<DirecaoResponse> consultarMinhaConta(@AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = direcao.consultarMinhaConta(UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/me/inativar")
    public ResponseEntity<DirecaoResponse> inativarMinhaConta(@Valid @RequestBody InativarDirecaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = direcao.inativarMinhaConta(request.versao(), UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PostMapping("/convites")
    public ResponseEntity<ConviteCadastroResponse> convidar(@Valid @RequestBody ConvidarDirecaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = cadastros.convidar(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(resposta);
    }

    @GetMapping
    public ResponseEntity<Page<DirecaoResponse>> listar(@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = direcao.listar(pagina, tamanho, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }

    @PutMapping("/me")
    public ResponseEntity<DirecaoResponse> atualizarMinhaConta(@Valid @RequestBody AtualizarDirecaoRequest request, @AuthenticationPrincipal UsuarioAutenticado autenticado) {

        var resposta = direcao.atualizarMinhaConta(request, UsuarioAtual.de(autenticado));

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}