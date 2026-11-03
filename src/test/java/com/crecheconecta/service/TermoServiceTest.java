package com.crecheconecta.service;

import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.repository.AceiteTermoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermoServiceTest {

    private final AceiteTermoRepository repository =
            mock(AceiteTermoRepository.class);

    private final AuditoriaService auditoria =
            mock(AuditoriaService.class);

    private final TermoService service =
            new TermoService(repository, auditoria);

    private final UUID usuarioId = UUID.randomUUID();

    @Test
    void deveExigirAceiteQuandoNaoExisteRegistro() {
        assertTrue(service.precisaAceitar(usuarioId));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveCompararVersaoDoUltimoAceite(boolean versaoAtual) {
        var aceite = new AceiteTermo();

        aceite.setVersaoTermo(
                versaoAtual
                        ? TermoService.VERSAO_ATUAL
                        : "versao-antiga"
        );

        when(repository.findFirstByUsuarioIdOrderByDataAceiteDesc(
                usuarioId
        )).thenReturn(Optional.of(aceite));

        assertEquals(
                !versaoAtual,
                service.precisaAceitar(usuarioId)
        );
    }

    @Test
    void deveRegistrarAceiteDaVersaoAtual() {
        service.registrarAceite(usuarioId);

        var captor = ArgumentCaptor.forClass(AceiteTermo.class);
        verify(repository).save(captor.capture());

        assertEquals(usuarioId, captor.getValue().getUsuarioId());

        assertEquals(
                TermoService.VERSAO_ATUAL,
                captor.getValue().getVersaoTermo()
        );

        verify(auditoria).aposCommit(
                "TERMO_ACEITO",
                usuarioId,
                null
        );
    }

    @Test
    void naoDeveAuditarSucessoSePersistenciaFalhar() {
        when(repository.save(any(AceiteTermo.class)))
                .thenThrow(
                        new IllegalStateException("Falha simulada")
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.registrarAceite(usuarioId)
        );

        verifyNoInteractions(auditoria);
    }
}