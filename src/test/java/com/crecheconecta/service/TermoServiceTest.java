package com.crecheconecta.service;

import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.repository.AceiteTermoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitarios do TermoService (termo de aceite dos pais com versionamento).
 *
 * Unitario puro: nao sobe o Spring. O repositorio e a auditoria sao mocks.
 * A regra central e precisaAceitar: o usuario precisa aceitar de novo
 * sempre que nunca aceitou OU quando a versao aceita e diferente da atual.
 */
@ExtendWith(MockitoExtension.class)
class TermoServiceTest {

    @Mock
    private AceiteTermoRepository repository;

    @Mock
    private AuditoriaService auditoria;

    private TermoService termoService;

    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        termoService = new TermoService(repository, auditoria);
    }

    // ---------- precisaAceitar: CAMINHO FELIZ ----------

    @Test
    void naoDevePrecisarAceitarQuandoJaAceitouAVersaoAtual() {
        // Arrange: ultimo aceite tem exatamente a versao atual
        AceiteTermo aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setVersaoTermo(TermoService.VERSAO_ATUAL);
        when(repository.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId))
                .thenReturn(Optional.of(aceite));

        // Act
        boolean precisa = termoService.precisaAceitar(usuarioId);

        // Assert
        assertFalse(precisa, "quem ja aceitou a versao atual nao precisa reaceitar");
    }

    // ---------- precisaAceitar: VIOLACAO / REGRA ----------

    @Test
    void devePrecisarAceitarQuandoNuncaAceitou() {
        // Arrange: nenhum aceite registrado
        when(repository.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId))
                .thenReturn(Optional.empty());

        // Act
        boolean precisa = termoService.precisaAceitar(usuarioId);

        // Assert
        assertTrue(precisa, "quem nunca aceitou precisa aceitar");
    }

    @Test
    void devePrecisarAceitarQuandoAVersaoAceitaEAntiga() {
        // Arrange: aceitou uma versao antiga, diferente da atual
        AceiteTermo aceiteAntigo = new AceiteTermo();
        aceiteAntigo.setUsuarioId(usuarioId);
        aceiteAntigo.setVersaoTermo("2000-01-01-v0");
        when(repository.findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId))
                .thenReturn(Optional.of(aceiteAntigo));

        // Act
        boolean precisa = termoService.precisaAceitar(usuarioId);

        // Assert
        assertTrue(precisa, "versao antiga obriga a reaceitar o termo");
    }

    // ---------- registrarAceite: INTERACAO COM DEPENDENCIAS (verify) ----------

    @Test
    void deveSalvarAceiteComAVersaoAtualERegistrarAuditoria() {
        // Act
        termoService.registrarAceite(usuarioId);

        // Assert: capturamos o que foi salvo para conferir os campos
        ArgumentCaptor<AceiteTermo> captor = ArgumentCaptor.forClass(AceiteTermo.class);
        verify(repository).save(captor.capture());
        AceiteTermo salvo = captor.getValue();

        assertEquals(usuarioId, salvo.getUsuarioId());
        assertEquals(TermoService.VERSAO_ATUAL, salvo.getVersaoTermo(),
                "o aceite deve registrar a versao atual do termo");

        // e a auditoria TERMO_ACEITO deve ser disparada uma vez, com recurso nulo
        verify(auditoria).aposCommit("TERMO_ACEITO", usuarioId, null);
        verifyNoMoreInteractions(auditoria);
    }

    @Test
    void naoDeveConsultarRepositorioAoRegistrarAceite() {
        // Act
        termoService.registrarAceite(usuarioId);

        // Assert: registrar aceite grava, mas nunca faz a consulta de "ultimo aceite"
        verify(repository, never()).findFirstByUsuarioIdOrderByDataAceiteDesc(any());
    }
}
