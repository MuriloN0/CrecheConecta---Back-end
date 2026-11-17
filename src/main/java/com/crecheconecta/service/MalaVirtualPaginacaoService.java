package com.crecheconecta.service;

import com.crecheconecta.dto.MalaVirtualTurmaResponse;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.MalaVirtualRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('PROFESSOR')")
public class MalaVirtualPaginacaoService {

    private static final int MALAS_POR_PAGINA = 5;

    private final MalaVirtualRepository malas;
    private final VinculoProfessorService vinculoProfessor;
    private final MalaVirtualSuporte suporte;

    public MalaVirtualPaginacaoService(
            MalaVirtualRepository malas,
            VinculoProfessorService vinculoProfessor,
            MalaVirtualSuporte suporte
    ) {
        this.malas = malas;
        this.vinculoProfessor = vinculoProfessor;
        this.suporte = suporte;
    }

    @Transactional(readOnly = true)
    public Page<MalaVirtualTurmaResponse> listar(UUID turmaId, int pagina, UsuarioAtual solicitante) {
        vinculoProfessor.exigirProfessorDaTurma(turmaId, solicitante);

        if (pagina < 0) {
            throw new RegraNegocioException("Informe uma página a partir de zero.");
        }

        var ordem = Sort.by(Sort.Direction.DESC, "data", "criadoEm", "id");
        var resultado = malas.findByTurmaId(turmaId, PageRequest.of(pagina, MALAS_POR_PAGINA, ordem));
        var itensPorMala = suporte.itensPorMala(resultado.getContent());

        return resultado.map(mala ->
                MalaVirtualTurmaResponse.de(mala, itensPorMala.getOrDefault(mala.getId(), List.of()))
        );
    }
}