package com.crecheconecta.controller;

import com.crecheconecta.service.ConsultaAuditoriaService;
import com.crecheconecta.service.ConsultaAuditoriaService.Assunto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('DIRECAO')")
public class AuditoriaController {

    private final ConsultaAuditoriaService auditoria;

    public AuditoriaController(ConsultaAuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping
    public ResponseEntity<ConsultaAuditoriaService.Pagina> listar(@RequestParam(required = false) Assunto assunto, @RequestParam(required = false) String acao, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim, @RequestParam(defaultValue = "0") int pagina) {

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auditoria.listar(assunto, acao, inicio, fim, pagina));
    }
}