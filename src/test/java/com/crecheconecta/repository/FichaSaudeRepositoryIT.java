package com.crecheconecta.repository;

import com.crecheconecta.entity.FichaSaude;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de INTEGRACAO de persistencia (@DataJpaTest) com banco H2 em memoria.
 *
 * Sobe apenas a camada JPA e um banco descartavel (perfil "test").
 * Cada teste monta o proprio cenario; nada depende de dados previos.
 *
 * Cobre: salvar e recuperar uma ficha, e a consulta customizada
 * findByAlunoId (fluxo que cruza repository + banco).
 */
@DataJpaTest
@ActiveProfiles("test")
class FichaSaudeRepositoryIT {

    @Autowired
    private FichaSaudeRepository repository;

    private FichaSaude novaFicha(UUID alunoId, String nome) {
        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        ficha.setNome(nome);
        ficha.setObservacoes("conteudo-cifrado-ficticio");
        Instant agora = Instant.now();
        ficha.setDataCriacao(agora);
        ficha.setDataAtualizacao(agora);
        return ficha;
    }

    // ---------- SALVAR E RECUPERAR ----------

    @Test
    void deveSalvarERecuperarFichaPeloId() {
        // Arrange
        UUID alunoId = UUID.randomUUID();
        FichaSaude salva = repository.save(novaFicha(alunoId, "Maria"));

        // Act
        FichaSaude recuperada = repository.findById(salva.getId()).orElseThrow();

        // Assert
        assertEquals(alunoId, recuperada.getAlunoId());
        assertEquals("Maria", recuperada.getNome());
        assertEquals("conteudo-cifrado-ficticio", recuperada.getObservacoes());
        assertNotNull(recuperada.getVersao(), "a @Version deve ser preenchida ao persistir");
    }

    // ---------- CONSULTA CUSTOMIZADA findByAlunoId ----------

    @Test
    void deveListarApenasAsFichasDoAlunoInformado() {
        // Arrange: duas fichas do aluno A e uma do aluno B
        UUID alunoA = UUID.randomUUID();
        UUID alunoB = UUID.randomUUID();
        repository.save(novaFicha(alunoA, "Ficha A1"));
        repository.save(novaFicha(alunoA, "Ficha A2"));
        repository.save(novaFicha(alunoB, "Ficha B1"));

        // Act
        List<FichaSaude> doAlunoA = repository.findByAlunoId(alunoA);

        // Assert
        assertEquals(2, doAlunoA.size(), "deve trazer apenas as 2 fichas do aluno A");
        assertTrue(doAlunoA.stream().allMatch(f -> f.getAlunoId().equals(alunoA)));
    }

    @Test
    void deveRetornarListaVaziaQuandoAlunoNaoTemFicha() {
        // Act
        List<FichaSaude> fichas = repository.findByAlunoId(UUID.randomUUID());

        // Assert
        assertTrue(fichas.isEmpty());
    }
}
