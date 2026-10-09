package com.crecheconecta.service;

import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.model.DadosFichaSaude;
import com.crecheconecta.repository.FichaSaudeRepository;
import com.crecheconecta.security.Perfil;
import com.crecheconecta.security.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitarios do FichaSaudeService (CRUD de ficha de saude).
 *
 * Unitario puro: repository, criptografia e auditoria sao mocks.
 * Usamos um Clock fixo para que as datas sejam previsiveis.
 *
 * Regras cobertas:
 *  - so a DIRECAO pode cadastrar/editar/excluir (exigirGestao);
 *  - as observacoes sao cifradas antes de salvar;
 *  - ficha de outro aluno nao vaza: retorna 404 (nao encontrada);
 *  - edicao com versao divergente gera conflito (optimistic lock).
 */
@ExtendWith(MockitoExtension.class)
class FichaSaudeServiceTest {

    @Mock
    private FichaSaudeRepository repository;

    @Mock
    private CriptografiaService cripto;

    @Mock
    private AuditoriaService auditoria;

    private FichaSaudeService service;

    private final UUID alunoId = UUID.randomUUID();
    private final UUID fichaId = UUID.randomUUID();
    private final UsuarioAtual direcao =
            new UsuarioAtual(UUID.randomUUID(), Perfil.DIRECAO);
    private final UsuarioAtual professor =
            new UsuarioAtual(UUID.randomUUID(), Perfil.PROFESSOR);

    // Clock fixo: 01/03/2026 12:00:00 UTC. Datas previsiveis nos testes.
    private final Instant momento = Instant.parse("2026-03-01T12:00:00Z");
    private final Clock clock = Clock.fixed(momento, ZoneOffset.UTC);

    @BeforeEach
    void preparar() {
        service = new FichaSaudeService(repository, cripto, auditoria, clock);
    }

    // ---------- CADASTRAR: CAMINHO FELIZ ----------

    @Test
    void deveCadastrarFichaCifrandoObservacoesERegistrandoAuditoria() {
        // Arrange
        DadosFichaSaude dados = new DadosFichaSaude("Maria", "Alergia a amendoim");
        when(cripto.cifrar("Alergia a amendoim")).thenReturn("CIFRADO==");
        when(repository.save(any(FichaSaude.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        UUID id = service.cadastrar(alunoId, dados, direcao);

        // Assert: captura o que foi salvo
        ArgumentCaptor<FichaSaude> captor = ArgumentCaptor.forClass(FichaSaude.class);
        verify(repository).save(captor.capture());
        FichaSaude salva = captor.getValue();

        assertEquals(alunoId, salva.getAlunoId());
        assertEquals("Maria", salva.getNome());
        assertEquals("CIFRADO==", salva.getObservacoes(),
                "as observacoes devem ser salvas cifradas, nunca em texto claro");
        assertEquals(momento, salva.getDataCriacao(), "data de criacao vem do Clock fixo");
        assertEquals(momento, salva.getDataAtualizacao());
        assertEquals(salva.getId(), id);

        verify(auditoria).aposCommit("FICHA_CADASTRADA", direcao.id(), salva.getId());
    }

    // ---------- CADASTRAR: VIOLACAO DE PERMISSAO ----------

    @Test
    void naoDevePermitirQueProfessorCadastreFicha() {
        // Arrange
        DadosFichaSaude dados = new DadosFichaSaude("Joao", "Nada a declarar");

        // Act
        AcessoNegadoException ex = assertThrows(
                AcessoNegadoException.class,
                () -> service.cadastrar(alunoId, dados, professor));

        // Assert
        assertTrue(ex.getMessage().contains("direção"),
                "mensagem deve dizer que so a direcao pode gerenciar");
        // nada pode ser salvo nem cifrado nem auditado quando o acesso e negado
        verify(repository, never()).save(any());
        verify(cripto, never()).cifrar(any());
        verifyNoInteractions(auditoria);
    }

    // ---------- VISUALIZAR / BUSCAR: 404 ANTI-VAZAMENTO ----------

    @Test
    void deveLancarNaoEncontradaQuandoFichaNaoExiste() {
        // Arrange
        when(repository.findById(fichaId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                FichaSaudeNaoEncontradaException.class,
                () -> service.visualizar(alunoId, fichaId, direcao));
    }

    @Test
    void naoDeveVazarFichaDeOutroAlunoRetornandoNaoEncontrada() {
        // Arrange: a ficha existe, mas pertence a OUTRO aluno
        FichaSaude deOutroAluno = new FichaSaude();
        deOutroAluno.setAlunoId(UUID.randomUUID()); // aluno diferente do solicitado
        when(repository.findById(fichaId)).thenReturn(Optional.of(deOutroAluno));

        // Act + Assert: deve responder "nao encontrada" (404), nao "acesso negado" (403)
        assertThrows(
                FichaSaudeNaoEncontradaException.class,
                () -> service.visualizar(alunoId, fichaId, direcao));
    }

    // ---------- VISUALIZAR: CAMINHO FELIZ (decifra) ----------

    @Test
    void deveVisualizarFichaDevolvendoObservacoesDecifradas() {
        // Arrange
        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        ficha.setNome("Ana");
        ficha.setObservacoes("CIFRADO==");
        ficha.setDataCriacao(momento);
        ficha.setDataAtualizacao(momento);
        when(repository.findById(fichaId)).thenReturn(Optional.of(ficha));
        when(cripto.decifrar("CIFRADO==")).thenReturn("Usa inalador");

        // Act
        FichaSaudeResponse resposta = service.visualizar(alunoId, fichaId, direcao);

        // Assert
        assertEquals("Usa inalador", resposta.observacoes(),
                "a resposta deve trazer as observacoes decifradas");
        verify(auditoria).registrar("FICHA_CONSULTADA", direcao.id(), fichaId);
    }

    // ---------- ATUALIZAR: CONFLITO DE VERSAO (caso-limite/violacao) ----------

    @Test
    void deveLancarConflitoQuandoVersaoForNula() {
        // Arrange
        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        when(repository.findById(fichaId)).thenReturn(Optional.of(ficha));
        DadosFichaSaude dados = new DadosFichaSaude("Novo nome", "obs");

        // Act + Assert: versao null nao pode atualizar
        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(alunoId, fichaId, dados, null, direcao));
        verify(cripto, never()).cifrar(any());
    }

    @Test
    void deveLancarConflitoQuandoVersaoForDivergente() {
        // Arrange: ficha esta na versao 3, cliente tenta salvar baseado na versao 1
        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        ficha.setVersao(3L);
        when(repository.findById(fichaId)).thenReturn(Optional.of(ficha));
        DadosFichaSaude dados = new DadosFichaSaude("Novo nome", "obs");

        // Act + Assert
        assertThrows(
                ConflitoVersaoException.class,
                () -> service.atualizar(alunoId, fichaId, dados, 1L, direcao));
    }
}
