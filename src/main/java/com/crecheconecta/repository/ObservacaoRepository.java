package com.crecheconecta.repository;

import com.crecheconecta.entity.Observacao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservacaoRepository extends JpaRepository<Observacao, UUID> {

    List<Observacao> findByAlunoId(UUID alunoId);
}
