package com.crecheconecta.service;

import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static com.crecheconecta.service.DadosAutenticacaoTest.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AprovacaoProfessorServiceTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final AuditoriaService auditoria =
            mock(AuditoriaService.class);

    private final AprovacaoProfessorService service =
            new AprovacaoProfessorService(usuarios, auditoria);

    private final Usuario diretor = usuario(Perfil.DIRECAO);
    private final Usuario professor = usuario(Perfil.PROFESSOR);

    private final UsuarioAtual solicitante =
            new UsuarioAtual(diretor.getId(), Perfil.DIRECAO);

    @BeforeEach
    void preparar() {
        professor.setAtivo(false);
        professor.setSituacaoCadastro(
                SituacaoCadastro.PENDENTE_APROVACAO
        );
    }

    @Test
    void deveAprovarCadastroPendente() {
        prepararPendente();

        var resposta = service.aprovar(
                professor.getId(),
                0L,
                solicitante
        );

        assertTrue(resposta.ativo());

        assertEquals(
                SituacaoCadastro.APROVADO,
                resposta.situacaoCadastro()
        );

        verify(usuarios).flush();

        verify(auditoria).aposCommit(
                "PROFESSOR_CADASTRO_APROVADO",
                diretor.getId(),
                professor.getId()
        );
    }

    @Test
    void deveRejeitarCadastroPendente() {
        prepararPendente();

        var resposta = service.rejeitar(
                professor.getId(),
                0L,
                solicitante
        );

        assertFalse(resposta.ativo());

        assertEquals(
                SituacaoCadastro.REJEITADO,
                resposta.situacaoCadastro()
        );

        verify(auditoria).aposCommit(
                "PROFESSOR_CADASTRO_REJEITADO",
                diretor.getId(),
                professor.getId()
        );
    }

    @Test
    void deveListarSomenteProfessoresPendentes() {
        autorizar();

        when(usuarios.findByPerfilAndSituacaoCadastro(
                eq(Perfil.PROFESSOR),
                eq(SituacaoCadastro.PENDENTE_APROVACAO),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(professor)));

        var resposta = service.listarPendentes(
                0,
                20,
                solicitante
        );

        assertEquals(
                professor.getId(),
                resposta.getContent().getFirst().id()
        );
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void deveRejeitarPaginacaoInvalida(int pagina, int tamanho) {
        autorizar();

        assertThrows(
                RegraNegocioException.class,
                () -> service.listarPendentes(
                        pagina,
                        tamanho,
                        solicitante
                )
        );
    }

    @Test
    void deveRejeitarVersaoAntigaSemAprovar() {
        prepararPendente();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.aprovar(
                        professor.getId(),
                        1L,
                        solicitante
                )
        );

        assertFalse(professor.isAtivo());
        verify(usuarios, never()).flush();
        verifyNoInteractions(auditoria);
    }

    @ParameterizedTest
    @EnumSource(
            value = SituacaoCadastro.class,
            names = {"APROVADO", "REJEITADO", "INATIVO"}
    )
    void deveRejeitarDecisaoSobreCadastroNaoPendente(
            SituacaoCadastro situacao
    ) {
        prepararPendente();
        professor.setSituacaoCadastro(situacao);

        assertThrows(
                RegraNegocioException.class,
                () -> service.aprovar(
                        professor.getId(),
                        0L,
                        solicitante
                )
        );

        assertThrows(
                RegraNegocioException.class,
                () -> service.rejeitar(
                        professor.getId(),
                        0L,
                        solicitante
                )
        );

        verifyNoInteractions(auditoria);
    }

    @Test
    void deveNegarDiretorInativo() {
        autorizar();
        diretor.setAtivo(false);

        assertThrows(
                AccessDeniedException.class,
                () -> service.aprovar(
                        professor.getId(),
                        0L,
                        solicitante
                )
        );

        verify(usuarios, never()).buscarPorIdComBloqueio(any());
    }

    @Test
    void deveNegarSolicitanteNaoAutenticado() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.aprovar(
                        professor.getId(),
                        0L,
                        null
                )
        );

        verifyNoInteractions(usuarios, auditoria);
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));
    }

    private void prepararPendente() {
        autorizar();

        when(usuarios.buscarPorIdComBloqueio(professor.getId()))
                .thenReturn(Optional.of(professor));
    }
}