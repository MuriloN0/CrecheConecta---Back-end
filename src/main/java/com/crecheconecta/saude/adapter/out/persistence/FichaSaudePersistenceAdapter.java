package com.crecheconecta.saude.adapter.out.persistence;

import com.crecheconecta.saude.application.port.out.FichaSaudeRepositoryPort;
import com.crecheconecta.saude.domain.model.FichaSaude;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FichaSaudePersistenceAdapter implements FichaSaudeRepositoryPort {

    private final SpringDataFichaSaudeRepository repository;

    public FichaSaudePersistenceAdapter(SpringDataFichaSaudeRepository repository) {
        this.repository = repository;
    }

    @Override
    public void salvar(FichaSaude ficha) {
        repository.save(FichaSaudeJpaEntity.de(ficha));
    }

    @Override
    public Optional<FichaSaude> buscarPorId(UUID fichaId) {
        return repository.findById(fichaId).map(FichaSaudeJpaEntity::toDomain);
    }

    @Override
    public List<FichaSaude> listarPorAluno(UUID alunoId) {
        return repository.findByAlunoIdOrderByDataCriacaoDesc(alunoId)
                .stream()
                .map(FichaSaudeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void excluir(UUID fichaId) {
        repository.deleteById(fichaId);
    }
}
