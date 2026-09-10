package com.crecheconecta.escolar.adapter.out.persistence;


import com.crecheconecta.escolar.application.exception.AlunoNaoEncontradoException;
import com.crecheconecta.escolar.application.exception.ConflitoVersaoException;
import com.crecheconecta.escolar.application.model.Pagina;
import com.crecheconecta.escolar.application.model.ResumoAluno;
import com.crecheconecta.escolar.application.port.out.AlunoRepositoryPort;
import com.crecheconecta.escolar.domain.model.Aluno;
import com.crecheconecta.escolar.domain.model.ContatoResponsavel;
import com.crecheconecta.escolar.domain.model.DadosAluno;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Repository
public class AlunoPersistenceAdapter implements AlunoRepositoryPort {
    private final SpringDataAlunoRepository repository;

    @Override
    @Transactional
    public Aluno salvar(Aluno aluno) {
        AlunoJpaEntity alunoJpaEntity;

        if(aluno.versao() == null){
            alunoJpaEntity = new AlunoJpaEntity();
            alunoJpaEntity.setId(aluno.id());
        } else {
            alunoJpaEntity = repository.findById(aluno.id())
                    .orElseThrow();
            if(!Objects.equals(alunoJpaEntity.getVersao(), aluno.versao())){
                throw new ConflitoVersaoException();
            }
        }

        copiarDados(aluno, alunoJpaEntity);

        AlunoJpaEntity salvo = repository.saveAndFlush(alunoJpaEntity);

        return paraDominio(salvo);
    }

    @Override
    @Transactional()
    public Optional<Aluno> buscarPorId(UUID id) {
        return repository.findById(id).map(this::paraDominio);
    }

    @Override
    @Transactional()
    public Pagina<ResumoAluno> listar(int pagina, int tamanho) {
        var pageable = PageRequest.of(
                pagina,
                tamanho,
                Sort.by("nome").ascending()
                        .and(Sort.by("id").ascending())
        );

        var resultado = repository.findAll(pageable);

        var itens = resultado.getContent().stream()
                .map(alunoJpaEntity -> new ResumoAluno(
                        alunoJpaEntity.getId(),
                        alunoJpaEntity.getNome(),
                        alunoJpaEntity.isAtivo(),
                        alunoJpaEntity.getVersao()
                ))
                .toList();

        return new Pagina<>(
                itens,
                resultado.getNumber(),
                resultado.getSize(),
                (int) resultado.getTotalElements(),
                resultado.getTotalPages()
        );
    }

    private void copiarDados(Aluno aluno, AlunoJpaEntity alunoJpaEntity) {
        DadosAluno dados = aluno.dadosAluno();

        alunoJpaEntity.setNome(dados.nome());
        alunoJpaEntity.setEndereco(dados.endereco());
        alunoJpaEntity.setEmailContato(dados.emailContato());
        alunoJpaEntity.setTelefoneContato(dados.telefoneContato());
        alunoJpaEntity.setAtivo(aluno.ativo());

        alunoJpaEntity.getResponsaveis().clear();

        for (ContatoResponsavel contato : dados.responsaveis()) {
            var responsavel = new ContatoResponsavelEmbeddable();

            responsavel.setNome(contato.nome());
            responsavel.setParentesco(contato.parentesco());
            responsavel.setEmail(contato.email());
            responsavel.setTelefone(contato.telefone());

            alunoJpaEntity.getResponsaveis().add(responsavel);
        }
    }

    private Aluno paraDominio(AlunoJpaEntity alunoJpaEntity) {
        var responsaveis = alunoJpaEntity.getResponsaveis().stream()
                .map(contato -> new ContatoResponsavel(
                        contato.getNome(),
                        contato.getParentesco(),
                        contato.getEmail(),
                        contato.getTelefone()
                ))
                .toList();

        var dados = new DadosAluno(
                alunoJpaEntity.getNome(),
                alunoJpaEntity.getEndereco(),
                alunoJpaEntity.getEmailContato(),
                alunoJpaEntity.getTelefoneContato(),
                responsaveis
        );

        return new Aluno(
                alunoJpaEntity.getId(),
                dados,
                alunoJpaEntity.isAtivo(),
                alunoJpaEntity.getVersao()
        );
    }

}
