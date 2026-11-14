package com.crecheconecta.service;

import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Transactional(readOnly = true)
    public TermoStatusResponse status(UUID usuarioId) {

        var termo = vigente();
        return new TermoStatusResponse(precisaAceitar(usuarioId, termo), termo.getIdentificador(), termo.getTermoUso(), termo.getPoliticaPrivacidade());
    }

    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {

        return precisaAceitar(usuarioId, vigente());
    }

    public void registrarAceite(UUID usuarioId) {

        var aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setVersaoTermo(vigente().getIdentificador());
        aceites.save(aceite);
        auditoria.aposCommit("TERMO_ACEITO", usuarioId, null);
    }

    public void registrarAceite(UUID usuarioId, String versao) {

        var termo = termos.buscarPorStatusComBloqueio(StatusTermo.VIGENTE).orElseThrow(ConflitoVersaoException::new);

        if (!termo.getIdentificador().equals(versao)) {
            throw new ConflitoVersaoException();
        }

        if (precisaAceitar(usuarioId, termo)) {
            registrarAceite(usuarioId);
        }
    }

    @Transactional(readOnly = true)
    public DocumentosCadastro documentosCadastro() {
        var termo = vigente();
        return new DocumentosCadastro(termo.getIdentificador(), termo.getTermoUso(), termo.getPoliticaPrivacidade());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarAceiteCadastro(UUID usuarioId, UUID alunoId, String versao) {

        var termo = termos.buscarPorStatusComBloqueio(StatusTermo.VIGENTE).orElseThrow(this::versaoAlterada);

        if (!termo.getIdentificador().equals(versao)) {

            throw versaoAlterada();
        }

        var aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setAlunoId(alunoId);
        aceite.setVersaoTermo(versao);
        aceites.save(aceite);
        auditoria.aposCommit("TERMO_ACEITO_NO_CADASTRO", usuarioId, alunoId);
    }

    private RegraNegocioException versaoAlterada() {

        return new RegraNegocioException("Os documentos foram atualizados. Clique em Atualizar documentos e confirme o novo aceite.");
    }

    private boolean precisaAceitar(UUID usuarioId, TermoVersao termo) {

        return aceites.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId)
                .map(aceite -> !aceite.getVersaoTermo().equals(termo.getIdentificador()))
                .orElse(true);
    }

    private TermoVersao vigente() {

        return termos.findByStatus(StatusTermo.VIGENTE).orElseThrow(() -> new IllegalStateException("Não há versão vigente dos termos."));
    }

    public record DocumentosCadastro(String versao, String termoUso, String politicaPrivacidade) {}
}