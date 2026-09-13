package com.crecheconecta.escolar.application.port.in;

import com.crecheconecta.escolar.application.model.Pagina;
import com.crecheconecta.escolar.application.model.ResumoAluno;
import com.crecheconecta.escolar.domain.model.Aluno;
import com.crecheconecta.escolar.domain.model.DadosAluno;

import java.util.UUID;

public interface GerenciarAlunoUseCase {

    Aluno cadastrar(DadosAluno dadosAluno);

    Aluno buscar(UUID id);

    Pagina<ResumoAluno> listar(int pagina, int tamanho);

    Aluno atualizar(UUID id, Long versao, DadosAluno dadosAluno);

    public void inativar(UUID id, Long versao);

}
