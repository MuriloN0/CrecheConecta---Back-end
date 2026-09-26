package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.TipoAtividade;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AtividadeValidation {

    public void validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new RegraNegocioException("Título é obrigatório");
        }
        if (titulo.trim().length() > 150) {
            throw new RegraNegocioException("Título excedeu 150 caracteres");
        }
    }

    public void validarPrazo(TipoAtividade tipo, LocalDate prazoConclusao) {
        if (tipo == TipoAtividade.CASA && prazoConclusao == null) {
            throw new RegraNegocioException("Prazo de conclusão é obrigatório para atividade de casa");
        }
    }
}