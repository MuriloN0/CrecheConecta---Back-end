package com.crecheconecta.escolar.application.port.out;

import com.crecheconecta.escolar.application.model.Pagina;
import com.crecheconecta.escolar.application.model.ResumoAluno;
import com.crecheconecta.escolar.domain.model.Aluno;

import java.util.Optional;
import java.util.UUID;

public interface AlunoRepositoryPort {
    Aluno salvar (Aluno aluno);

    Optional<Aluno> buscarPorId(UUID id);

    Pagina<ResumoAluno> listar(int pagina, int tamanho);

}
