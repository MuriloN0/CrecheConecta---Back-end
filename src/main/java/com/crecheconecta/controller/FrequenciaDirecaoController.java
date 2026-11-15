package com.crecheconecta.controller;

import com.crecheconecta.service.FrequenciaDirecaoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/turmas/frequencia")
@PreAuthorize("hasRole('DIRECAO')")
public class FrequenciaDirecaoController {

    private final FrequenciaDirecaoService frequencia;

    public FrequenciaDirecaoController(FrequenciaDirecaoService frequencia) {
        this.frequencia = frequencia;
    }

    @GetMapping
    public ResponseEntity<FrequenciaDirecaoService.Resumo> consultar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim, @RequestParam(required = false) UUID turmaId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(frequencia.consultar(inicio, fim, turmaId));
    }
}