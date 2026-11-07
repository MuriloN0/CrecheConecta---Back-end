package com.crecheconecta.service;

import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Aceite dos termos pelo usuário. O texto e a versão vigente vêm do banco (tabela termos_versoes),
 * gerenciados pela direção em {@link TermoVersaoService}. Quando uma nova versão é publicada,
 * todos precisam aceitar de novo, porque o último aceite deixa de ser da versão vigente.
 */
@Service
@Transactional
public class TermoService {

    private final AceiteTermoRepository aceites;
    private final TermoVersaoRepository termos;
    private final AuditoriaService auditoria;

    public TermoService(AceiteTermoRepository aceites, TermoVersaoRepository termos, AuditoriaService auditoria) {
        this.aceites = aceites;
        this.termos = termos;
        this.auditoria = auditoria;
    }

    /** Textos da versão vigente + se este usuário ainda precisa aceitá-la. */
    @Transactional(readOnly = true)
    public TermoStatusResponse status(UUID usuarioId) {
        TermoVersao vigente = vigente();
        return new TermoStatusResponse(
                precisaAceitar(usuarioId, vigente),
                vigente.getIdentificador(),
                vigente.getTermoUso(),
                vigente.getPoliticaPrivacidade()
        );
    }

    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {
        return precisaAceitar(usuarioId, vigente());
    }

    public void registrarAceite(UUID usuarioId) {
        AceiteTermo aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setVersaoTermo(vigente().getIdentificador());
        aceites.save(aceite);
        auditoria.aposCommit("TERMO_ACEITO", usuarioId, null);
    }

    private boolean precisaAceitar(UUID usuarioId, TermoVersao vigente) {
        return aceites
                .findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId)
                .map(aceite -> !aceite.getVersaoTermo().equals(vigente.getIdentificador()))
                .orElse(true);
    }

    // A migration V27 cria a versão inicial e o banco impede apagar a vigente, então sempre existe uma.
    private TermoVersao vigente() {
        return termos.findByStatus(StatusTermo.VIGENTE)
                .orElseThrow(() -> new IllegalStateException("Não há versão vigente dos termos."));
    }
}
