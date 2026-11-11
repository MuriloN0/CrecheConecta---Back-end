package com.crecheconecta.service;

import com.crecheconecta.dto.ChamadaMesResponse;
import com.crecheconecta.repository.PresencaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Portal dos pais: faltas e presenças (chamada) do filho, mês a mês. Só leitura.
 */
@Service
public class ChamadaFilhoService {

    // O Clock do projeto é UTC; o "mês atual" precisa ser o do Brasil (perto da meia-noite do dia 31 faria diferença).
    private static final ZoneId FUSO_CRECHE = ZoneId.of("America/Sao_Paulo");

    private final VinculoResponsavelService acessoResponsavel;
    private final PresencaRepository presencas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ChamadaFilhoService(VinculoResponsavelService acessoResponsavel, PresencaRepository presencas, AuditoriaService auditoria, Clock clock) {
        this.acessoResponsavel = acessoResponsavel;
        this.presencas = presencas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /**
     * @param mes mês desejado; se null, usa o mês atual.
     */
    @Transactional(readOnly = true)
    public ChamadaMesResponse consultarMes(UUID alunoId, YearMonth mes, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        YearMonth mesConsultado = mes != null ? mes : YearMonth.now(clock.withZone(FUSO_CRECHE));

        var registros = presencas.findByAlunoIdAndDataBetweenOrderByDataAsc(alunoId, mesConsultado.atDay(1), mesConsultado.atEndOfMonth());

        auditoria.registrar("CHAMADA_CONSULTADA", solicitante.id(), alunoId);
        return ChamadaMesResponse.de(alunoId, mesConsultado, registros);
    }
}
