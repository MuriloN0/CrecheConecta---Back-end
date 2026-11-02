package com.crecheconecta.service;

import com.crecheconecta.dto.AtividadeResponseDTO;
import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Portal dos pais: "Atividades para casa" do filho.
 * Só LÊ o módulo de atividades (Belim/Gabriel): descobre a turma atual do filho
 * e devolve as atividades do tipo CASA dessa turma.
 */
@Service
public class AtividadesCasaService {

    // Prazo mais próximo primeiro; atividades sem prazo vão para o fim; empate = mais recente primeiro.
    private static final Comparator<AtividadeEntity> ORDEM = Comparator
            .comparing(AtividadeEntity::getPrazoConclusao, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AtividadeEntity::getDataCriacao, Comparator.reverseOrder());

    private final VinculoResponsavelService acessoResponsavel;
    private final AtividadeRepository atividades;
    private final AuditoriaService auditoria;

    public AtividadesCasaService(VinculoResponsavelService acessoResponsavel, AtividadeRepository atividades, AuditoriaService auditoria) {
        this.acessoResponsavel = acessoResponsavel;
        this.atividades = atividades;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<AtividadeResponseDTO> listar(UUID alunoId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        List<AtividadeResponseDTO> lista = acessoResponsavel.turmaAtualDoAluno(alunoId)
                .map(turmaId -> atividades.findByTurmaIdAndTipo(turmaId, TipoAtividade.CASA))
                .orElse(List.of())
                .stream()
                .sorted(ORDEM)
                .map(AtividadeResponseDTO::fromEntity)
                .toList();

        auditoria.registrar("ATIVIDADES_CASA_LISTADAS", solicitante.id(), alunoId);
        return lista;
    }
}
