package com.crecheconecta.escolar.application.service;

import com.crecheconecta.escolar.application.exception.AlunoNaoEncontradoException;
import com.crecheconecta.escolar.application.exception.ConflitoVersaoException;
import com.crecheconecta.escolar.application.model.Pagina;
import com.crecheconecta.escolar.application.model.ResumoAluno;
import com.crecheconecta.escolar.application.port.in.GerenciarAlunoUseCase;
import com.crecheconecta.escolar.application.port.out.AlunoRepositoryPort;
import com.crecheconecta.escolar.domain.exception.RegraNegocioException;
import com.crecheconecta.escolar.domain.model.Aluno;
import com.crecheconecta.escolar.domain.model.DadosAluno;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class AlunoService implements GerenciarAlunoUseCase {
    private final AlunoRepositoryPort alunoRepositoryPort;


    @Override
    public Aluno cadastrar(DadosAluno dadosAluno) {
        return alunoRepositoryPort.salvar(Aluno.cadastrar(dadosAluno));
    }

    @Override
    public Aluno buscar(UUID id){
        if(id == null){
            throw new RegraNegocioException("ID é obrigatório");
        }

        return alunoRepositoryPort.buscarPorId(id)
                .orElseThrow(AlunoNaoEncontradoException::new);
    }

    @Override
    public Pagina<ResumoAluno> listar(int pagina, int tamanho){
        if (pagina < 0 || tamanho < 1 || tamanho > 50){
            throw new RegraNegocioException("Página deve ser maior ou igual a zero e tamanho deve estar entre 1 e 50.");
        }
        return alunoRepositoryPort.listar(pagina, tamanho);
    }

    @Override
    public Aluno atualizar(UUID id, Long versao, DadosAluno dadosAluno) {
        Aluno aluno = buscar(id);
        validarVersao(aluno, versao);
        return alunoRepositoryPort.salvar(aluno.atualizar(dadosAluno));
    }

    @Override
    public void inativar(UUID id, Long versao) {
        Aluno aluno = buscar(id);
        validarVersao(aluno, versao);
        alunoRepositoryPort.salvar(aluno.inativar());
    }

    private void validarVersao(Aluno aluno, Long versao) {
        if(versao == null || versao < 0){
            throw new RegraNegocioException("Uma versão válida é obrigatória");
        }

        if(!Objects.equals(aluno.versao(), versao)){
            throw new ConflitoVersaoException();
        }
    }

}
