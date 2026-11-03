package com.crecheconecta.service;

import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.model.DadosFichaSaude;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.FichaSaudeRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FichaSaudeServiceTest {

    private final FichaSaudeRepository repository =
            mock(FichaSaudeRepository.class);

    private final CriptografiaService cripto =
            mock(CriptografiaService.class);

    private final AuditoriaService auditoria =
            mock(AuditoriaService.class);

    private final AlunoRepository alunos =
            mock(AlunoRepository.class);

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final Instant agora =
            Instant.parse("2026-11-02T12:00:00Z");

    private final FichaSaudeService service =
            new FichaSaudeService(
                    repository,
                    cripto,
                    auditoria,
                    Clock.fixed(agora, ZoneOffset.UTC),
                    alunos,
                    usuarios
            );

    private final Usuario diretor = usuario(Perfil.DIRECAO);

    private final UsuarioAtual solicitante =
            new UsuarioAtual(diretor.getId(), Perfil.DIRECAO);

    private final UUID alunoId = UUID.randomUUID();
    private final FichaSaude ficha = new FichaSaude();

    @BeforeEach
    void preparar() {
        ficha.setAlunoId(alunoId);
        ficha.setNome("Ficha");
        ficha.setObservacoes("conteudo-cifrado");
        ficha.setVersao(0L);
        ficha.setDataCriacao(agora.minusSeconds(3600));
        ficha.setDataAtualizacao(ficha.getDataCriacao());
    }

    @Test
    void deveCifrarObservacoesAntesDePersistir() {
        autorizar();

        when(cripto.cifrar("Observação"))
                .thenReturn("cifrado");

        var id = service.cadastrar(
                alunoId,
                new DadosFichaSaude("Ficha", "Observação"),
                solicitante
        );

        var captor = ArgumentCaptor.forClass(FichaSaude.class);
        verify(repository).save(captor.capture());

        assertEquals(
                "cifrado",
                captor.getValue().getObservacoes()
        );

        assertEquals(
                alunoId,
                captor.getValue().getAlunoId()
        );

        assertEquals(
                agora,
                captor.getValue().getDataCriacao()
        );

        verify(auditoria).aposCommit(
                "FICHA_CADASTRADA",
                diretor.getId(),
                id
        );
    }

    @Test
    void deveDecifrarSomenteNaVisualizacao() {
        prepararFicha();

        when(cripto.decifrar("conteudo-cifrado"))
                .thenReturn("Observação");

        var resposta = service.visualizar(
                alunoId,
                ficha.getId(),
                solicitante
        );

        assertEquals("Observação", resposta.observacoes());

        verify(auditoria).registrar(
                "FICHA_CONSULTADA",
                diretor.getId(),
                ficha.getId()
        );
    }

    @Test
    void deveListarResumosSemDecifrarObservacoes() {
        autorizar();

        when(repository.findByAlunoId(alunoId))
                .thenReturn(List.of(ficha));

        assertEquals(
                1,
                service.listar(alunoId, solicitante).size()
        );

        verifyNoInteractions(cripto);
    }

    @Test
    void deveAtualizarCifrandoNovasObservacoes() {
        prepararFicha();

        when(cripto.cifrar("Nova observação"))
                .thenReturn("novo-cifrado");

        when(cripto.decifrar("novo-cifrado"))
                .thenReturn("Nova observação");

        var criadoEm = ficha.getDataCriacao();

        var resposta = service.atualizar(
                alunoId,
                ficha.getId(),
                new DadosFichaSaude(
                        "Novo nome",
                        "Nova observação"
                ),
                0L,
                solicitante
        );

        assertEquals(
                "Nova observação",
                resposta.observacoes()
        );

        assertEquals(
                "novo-cifrado",
                ficha.getObservacoes()
        );

        assertEquals(criadoEm, ficha.getDataCriacao());
        assertEquals(agora, ficha.getDataAtualizacao());

        verify(repository).flush();
    }

    @Test
    void deveRejeitarVersaoAntigaSemAlterarFicha() {
        prepararFicha();

        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(
                        alunoId,
                        ficha.getId(),
                        new DadosFichaSaude(
                                "Novo",
                                "Observação"
                        ),
                        1L,
                        solicitante
                )
        );

        assertEquals("Ficha", ficha.getNome());

        verifyNoInteractions(cripto, auditoria);
        verify(repository, never()).flush();
    }

    @Test
    void deveImpedirAcessoAFichaDeOutroAluno() {
        prepararFicha();
        ficha.setAlunoId(UUID.randomUUID());

        assertThrows(
                FichaSaudeNaoEncontradaException.class,
                () -> service.visualizar(
                        alunoId,
                        ficha.getId(),
                        solicitante
                )
        );

        verifyNoInteractions(cripto, auditoria);
    }

    @Test
    void deveExcluirSomenteFichaLocalizadaDoAluno() {
        prepararFicha();

        service.excluir(
                alunoId,
                ficha.getId(),
                solicitante
        );

        verify(repository).delete(ficha);

        verify(auditoria).aposCommit(
                "FICHA_EXCLUIDA",
                diretor.getId(),
                ficha.getId()
        );
    }

    @Test
    void deveRejeitarAlunoInexistente() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));

        assertThrows(
                AlunoNaoEncontradoException.class,
                () -> service.listar(
                        alunoId,
                        solicitante
                )
        );

        verifyNoInteractions(repository, cripto);
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"PROFESSOR", "RESPONSAVEL"}
    )
    void deveNegarOutrosPerfis(Perfil perfil) {
        assertThrows(
                AcessoNegadoException.class,
                () -> service.visualizar(
                        alunoId,
                        ficha.getId(),
                        new UsuarioAtual(
                                UUID.randomUUID(),
                                perfil
                        )
                )
        );

        verifyNoInteractions(repository, cripto);
    }

    @Test
    void deveNegarDiretorInativo() {
        diretor.setAtivo(false);

        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));

        assertThrows(
                AcessoNegadoException.class,
                () -> service.listar(
                        alunoId,
                        solicitante
                )
        );

        verifyNoInteractions(repository, cripto);
    }

    private void autorizar() {
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));

        when(alunos.existsById(alunoId))
                .thenReturn(true);
    }

    private void prepararFicha() {
        autorizar();

        when(repository.findById(ficha.getId()))
                .thenReturn(Optional.of(ficha));
    }
}