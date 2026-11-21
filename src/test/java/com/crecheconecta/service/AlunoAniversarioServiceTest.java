package com.crecheconecta.service;

import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.crecheconecta.service.DadosAutenticacaoTest.usuario;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlunoAniversarioServiceTest {

    private final AlunoRepository alunos = mock(AlunoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private final AlunoService service = new AlunoService(
            alunos,
            usuarios,
            mock(AuditoriaService.class),
            Clock.systemUTC(),
            mock(MatriculaTurmaRepository.class)
    );

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    void combinaMesNomeSituacaoEPaginacao(int mes) {
        var diretor = usuario(Perfil.DIRECAO);
        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));

        var aluno = new Aluno();
        aluno.setId(UUID.randomUUID());
        aluno.setNome("Ana");
        aluno.setDataNascimento(LocalDate.of(2023, mes, 10));

        when(alunos.buscarPorMesNascimento(
                eq(mes), eq("Ana"), eq(true), any()
        )).thenReturn(new PageImpl<>(List.of(aluno)));

        var resposta = service.listar(
                2, 10, true, "  Ana  ", mes,
                new UsuarioAtual(diretor.getId(), Perfil.DIRECAO)
        );

        assertEquals(
                aluno.getId(),
                resposta.getContent().getFirst().id()
        );

        var pagina = ArgumentCaptor.forClass(Pageable.class);

        verify(alunos).buscarPorMesNascimento(
                eq(mes), eq("Ana"), eq(true), pagina.capture()
        );

        assertEquals(2, pagina.getValue().getPageNumber());
        assertEquals(10, pagina.getValue().getPageSize());

        verify(alunos, never()).findByAtivo(anyBoolean(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 13})
    void rejeitaMesForaDoIntervaloSemConsultarAlunos(int mes) {
        var diretor = usuario(Perfil.DIRECAO);

        when(usuarios.findById(diretor.getId()))
                .thenReturn(Optional.of(diretor));

        assertThrows(RegraNegocioException.class, () -> service.listar(
                0, 20, null, null, mes,
                new UsuarioAtual(diretor.getId(), Perfil.DIRECAO)
        ));

        verifyNoInteractions(alunos);
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"PROFESSOR", "RESPONSAVEL"})
    void filtroContinuaRestritoADirecao(Perfil perfil) {
        assertThrows(AccessDeniedException.class, () -> service.listar(
                0, 20, null, null, 6,
                new UsuarioAtual(UUID.randomUUID(), perfil)
        ));

        verifyNoInteractions(alunos);
    }
}