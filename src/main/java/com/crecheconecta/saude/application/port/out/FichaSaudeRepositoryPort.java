package com.crecheconecta.saude.application.port.out;

import com.crecheconecta.saude.domain.model.FichaSaude;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FichaSaudeRepositoryPort {
    void salvar(FichaSaude ficha);
    Optional<FichaSaude> buscarPorId(UUID fichaId);
    List<FichaSaude> listarPorAluno(UUID alunoId);
    void excluir(UUID fichaId);
}
