package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;
import org.springframework.stereotype.Component;

@Component
public class AtividadeTituloValidation {

    public void validar(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new RegraNegocioException("Título é obrigatório");
        }
        if (titulo.trim().length() > 150) {
            throw new RegraNegocioException("Título excedeu 150 caracteres");
        }
    }
}
