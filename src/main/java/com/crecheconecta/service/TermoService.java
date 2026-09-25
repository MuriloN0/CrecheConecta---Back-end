package com.crecheconecta.service;

import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.repository.AceiteTermoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TermoService {
    public static final String VERSAO_ATUAL = "2026-09-25-v1";

    private static final String TEXTO_ATUAL =
            "Ao utilizar o CrecheConecta, você autoriza o tratamento dos dados do seu filho "
                    + "para fins escolares e de comunicação com a creche. Os dados sensíveis de saúde "
                    + "são protegidos e usados apenas pela equipe autorizada. Você pode solicitar acesso "
                    + "ou correção dos dados a qualquer momento junto à direção.";

    private final AceiteTermoRepository repository;
    private final AuditoriaService auditoria;

    public TermoService(AceiteTermoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }
    @Transactional(readOnly = true)
    public String versaoAtual() {
        return VERSAO_ATUAL;
    }

    @Transactional(readOnly = true)
    public String textoAtual() {
        return TEXTO_ATUAL;
    }
    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {
        return repository
                .findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId)
                .map(aceite -> !aceite.getVersaoTermo().equals(VERSAO_ATUAL))
                .orElse(true);
    }
    public void registrarAceite(UUID usuarioId) {
        AceiteTermo aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setVersaoTermo(VERSAO_ATUAL);
        repository.save(aceite);
        auditoria.aposCommit("TERMO_ACEITO", usuarioId, null);
    }
}