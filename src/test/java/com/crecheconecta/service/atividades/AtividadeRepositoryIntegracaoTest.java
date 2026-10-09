package com.crecheconecta.service.atividades;

import com.crecheconecta.entity.AtividadeEntity;
import com.crecheconecta.model.TipoAtividade;
import com.crecheconecta.repository.AtividadeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:atividades-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class AtividadeRepositoryIntegracaoTest {

    @Autowired
    private AtividadeRepository atividadeRepository;

    @Test
    void salvaEBuscaPorTurmaETipo() {
        // Arrange
        UUID turmaId = UUID.randomUUID();

        AtividadeEntity casa = new AtividadeEntity();
        casa.setId(UUID.randomUUID());
        casa.setTurmaId(turmaId);
        casa.setProfessorId(UUID.randomUUID());
        casa.setTipo(TipoAtividade.CASA);
        casa.setTitulo("Para casa");
        casa.setDataCriacao(Instant.now());

        AtividadeEntity dia = new AtividadeEntity();
        dia.setId(UUID.randomUUID());
        dia.setTurmaId(turmaId);
        dia.setProfessorId(UUID.randomUUID());
        dia.setTipo(TipoAtividade.DIA);
        dia.setTitulo("Do dia");
        dia.setDataCriacao(Instant.now());

        // Act
        atividadeRepository.save(casa);
        atividadeRepository.save(dia);
        List<AtividadeEntity> filtradas =
                atividadeRepository.findByTurmaIdAndTipo(turmaId, TipoAtividade.CASA);
        List<AtividadeEntity> daTurma = atividadeRepository.findByTurmaId(turmaId);

        // Assert
        assertEquals(1, filtradas.size());
        assertEquals("Para casa", filtradas.getFirst().getTitulo());
        assertTrue(daTurma.size() >= 2);
    }
}
