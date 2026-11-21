package com.crecheconecta.service;

import com.crecheconecta.dto.TermoStatusResponse;
import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Clock;

@Service
@Transactional
public class TermoService {

    private final AceiteTermoRepository aceites;
    private final TermoVersaoRepository termos;
    private final AuditoriaService auditoria;
    private final UsuarioRepository usuarios;
    private final SessaoRepository sessoes;
    private final Clock clock;

    public TermoService(
            AceiteTermoRepository aceites,
            TermoVersaoRepository termos,
            AuditoriaService auditoria,
            UsuarioRepository usuarios,
            SessaoRepository sessoes,
            Clock clock
    ) {
        this.aceites = aceites;
        this.termos = termos;
        this.auditoria = auditoria;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TermoStatusResponse status(UUID usuarioId) {
        var termo = vigente();

        return new TermoStatusResponse(
                precisaAceitar(usuarioId, termo),
                termo.getIdentificador(),
                termo.getTermoUso(),
                termo.getPoliticaPrivacidade()
        );
    }

    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {
        return precisaAceitar(usuarioId, vigente());
    }

    @Transactional(readOnly = true)
    public boolean possuiAceitePendente(UUID usuarioId) {
        return termos.findByStatus(StatusTermo.VIGENTE)
                .map(vigente -> precisaAceitar(usuarioId, vigente))
                .orElse(false);
    }

    public void registrarAceite(UUID usuarioId) {
        bloquearUsuario(usuarioId);
        salvarAceite(usuarioId, vigente().getIdentificador());
    }

    private void salvarAceite(UUID usuarioId, String versao) {
        var aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setVersaoTermo(versao);

        aceites.save(aceite);
        auditoria.aposCommit("TERMO_ACEITO", usuarioId, null);
    }

    public void registrarAceite(UUID usuarioId, String versao) {
        bloquearUsuario(usuarioId);

        var termo = termos.buscarPorStatusComBloqueio(StatusTermo.VIGENTE)
                .orElseThrow(ConflitoVersaoException::new);

        if (!termo.getIdentificador().equals(versao)) {
            throw new ConflitoVersaoException();
        }

        if (precisaAceitar(usuarioId, termo)) {
            salvarAceite(usuarioId, termo.getIdentificador());
        }
    }

    @Transactional(readOnly = true)
    public DocumentosCadastro documentosCadastro() {
        var termo = vigente();

        return new DocumentosCadastro(
                termo.getIdentificador(),
                termo.getTermoUso(),
                termo.getPoliticaPrivacidade()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarAceiteCadastro(UUID usuarioId, UUID alunoId, String versao) {
        var termo = termos.buscarPorStatusComBloqueio(StatusTermo.VIGENTE)
                .orElseThrow(this::versaoAlterada);

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
        return new RegraNegocioException(
                "Os documentos foram atualizados. Clique em Atualizar documentos e confirme o novo aceite."
        );
    }

    private boolean precisaAceitar(UUID usuarioId, TermoVersao termo) {
        return aceites.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId)
                .map(aceite -> aceite.getRevogadoEm() != null
                        || !aceite.getVersaoTermo().equals(termo.getIdentificador()))
                .orElse(true);
    }

    private TermoVersao vigente() {
        return termos.findByStatus(StatusTermo.VIGENTE)
                .orElseThrow(() ->
                        new IllegalStateException("Não há versão vigente dos termos.")
                );
    }

    public record DocumentosCadastro(
            String versao,
            String termoUso,
            String politicaPrivacidade
    ) {}

    public void revogarAceite(UsuarioAtual atual) {
        if (atual == null || atual.perfil() != Perfil.RESPONSAVEL) {
            throw new AccessDeniedException(
                    "Operacao restrita ao proprio responsavel."
            );
        }

        var usuario = usuarios.buscarPorIdComBloqueio(atual.id())
                .filter(pessoa ->
                        pessoa.isAtivo() && pessoa.getPerfil() == Perfil.RESPONSAVEL
                )
                .orElseThrow(() ->
                        new AccessDeniedException("Conta indisponivel.")
                );

        var agora = clock.instant();
        boolean alterou = false;

        var registros = aceites.findByUsuarioIdOrderByDataAceiteDesc(usuario.getId());

        for (var aceite : registros) {
            if (aceite.getRevogadoEm() == null) {
                aceite.setRevogadoEm(agora);
                alterou = true;
            }
        }

        aceites.saveAll(registros);
        sessoes.revogarTodasDoUsuario(usuario.getId(), agora);

        if (alterou) {
            auditoria.aposCommit(
                    "TERMO_ACEITE_REVOGADO",
                    usuario.getId(),
                    null
            );
        }
    }

    private void bloquearUsuario(UUID usuarioId) {
        usuarios.buscarPorIdComBloqueio(usuarioId)
                .orElseThrow(() ->
                        new AccessDeniedException("Conta indisponivel.")
                );
    }
}