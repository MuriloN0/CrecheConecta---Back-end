package com.crecheconecta.service;

import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.entity.StatusTermo;
import com.crecheconecta.entity.TermoVersao;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.TermoVersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermoServiceTest {

    private static final String VIGENTE = "2026-11-09-v3";

    private final AceiteTermoRepository aceites = mock(AceiteTermoRepository.class);
    private final TermoVersaoRepository termos = mock(TermoVersaoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SessaoRepository sessoes = mock(SessaoRepository.class);

    private final TermoService service = new TermoService(
            aceites, termos, auditoria, usuarios, sessoes, Clock.systemUTC()
    );

    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void prepararVersaoVigente() {
        var pessoa = new Usuario();
        pessoa.setId(usuarioId);

        when(usuarios.buscarPorIdComBloqueio(usuarioId))
                .thenReturn(Optional.of(pessoa));

        var vigente = new TermoVersao();
        vigente.setIdentificador(VIGENTE);
        vigente.setTermoUso("Texto do termo vigente");
        vigente.setPoliticaPrivacidade("Texto da política vigente");
        vigente.setStatus(StatusTermo.VIGENTE);

        when(termos.findByStatus(StatusTermo.VIGENTE))
                .thenReturn(Optional.of(vigente));
    }

    @Test
    void deveExigirAceiteQuandoNaoExisteRegistro() {
        assertTrue(service.precisaAceitar(usuarioId));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveCompararAVersaoDoUltimoAceiteComAVigenteDoBanco(boolean versaoAtual) {
        var aceite = new AceiteTermo();
        aceite.setVersaoTermo(versaoAtual ? VIGENTE : "versao-antiga");

        when(aceites.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId))
                .thenReturn(Optional.of(aceite));

        assertEquals(!versaoAtual, service.precisaAceitar(usuarioId));
    }

    @Test
    void deveExigirNovoAceiteQuandoOVigenteFoiRevogado() {
        var aceite = new AceiteTermo();
        aceite.setVersaoTermo(VIGENTE);
        aceite.setRevogadoEm(Instant.now());

        when(aceites.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId))
                .thenReturn(Optional.of(aceite));

        assertTrue(service.precisaAceitar(usuarioId));
    }

    @Test
    void deveDevolverOsTextosDaVersaoVigente() {
        var status = service.status(usuarioId);

        assertTrue(status.precisaAceitar());
        assertEquals(VIGENTE, status.versao());
        assertEquals("Texto do termo vigente", status.termoUso());
        assertEquals("Texto da política vigente", status.politicaPrivacidade());
    }

    @Test
    void deveRegistrarAceiteDaVersaoVigente() {
        service.registrarAceite(usuarioId);

        var captor = ArgumentCaptor.forClass(AceiteTermo.class);
        verify(aceites).save(captor.capture());

        assertEquals(usuarioId, captor.getValue().getUsuarioId());
        assertEquals(VIGENTE, captor.getValue().getVersaoTermo());
        verify(auditoria).aposCommit("TERMO_ACEITO", usuarioId, null);
    }

    @Test
    void naoDeveAuditarSucessoSePersistenciaFalhar() {
        when(aceites.save(any(AceiteTermo.class)))
                .thenThrow(new IllegalStateException("Falha simulada"));

        assertThrows(
                IllegalStateException.class,
                () -> service.registrarAceite(usuarioId)
        );

        verifyNoInteractions(auditoria);
    }

    @Test
    void deveFalharSeNaoHouverVersaoVigente() {
        when(termos.findByStatus(StatusTermo.VIGENTE))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> service.precisaAceitar(usuarioId)
        );
    }
}