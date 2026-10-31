package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.model.TipoAtividade;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AtividadePrazoValidation {

    public void validar(TipoAtividade tipo, LocalDate prazoConclusao) {
        if (tipo == TipoAtividade.CASA && prazoConclusao == null) {
            throw new RegraNegocioException("Prazo de conclusão é obrigatório para atividade de casa");
        }
    }
}
