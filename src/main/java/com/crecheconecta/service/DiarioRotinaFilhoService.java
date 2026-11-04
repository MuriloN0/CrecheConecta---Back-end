package com.crecheconecta.service;

import com.crecheconecta.dto.DiarioRotinaResponse;
import com.crecheconecta.exception.DiarioRotinaNaoEncontradoException;
import com.crecheconecta.repository.DiarioRotinaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal dos pais: Diário de Rotina Digital do filho. Só leitura.
 */
@Service
public class DiarioRotinaFilhoService {

    private final VinculoResponsavelService acessoResponsavel;
    private final DiarioRotinaRepository diarios;
    private final AuditoriaService auditoria;

    public DiarioRotinaFilhoService(VinculoResponsavelService acessoResponsavel, DiarioRotinaRepository diarios, AuditoriaService auditoria) {
        this.acessoResponsavel = acessoResponsavel;
        this.diarios = diarios;
        this.auditoria = auditoria;
    }

    /**
     * @param data dia desejado; se null, devolve o diário mais recente.
     */
    @Transactional(readOnly = true)
    public DiarioRotinaResponse consultar(UUID alunoId, LocalDate data, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        var diario = (data != null ? diarios.findByAlunoIdAndData(alunoId, data) : diarios.findFirstByAlunoIdOrderByDataDesc(alunoId)).orElseThrow(DiarioRotinaNaoEncontradoException::new);

        auditoria.registrar("DIARIO_ROTINA_CONSULTADO", solicitante.id(), diario.getId());
        return DiarioRotinaResponse.de(diario);
    }
}
