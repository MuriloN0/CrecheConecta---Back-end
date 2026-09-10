package com.crecheconecta.escolar.adapter.in.web;

import com.crecheconecta.escolar.domain.model.Aluno;

import java.util.List;
import java.util.UUID;

public record AlunoResponse(
        UUID id,
        String nome,
        String endereco,
        String emailContato,
        String telefoneContato,
        boolean ativo,
        String versao,
        List<ResponsavelResponse> responsaveis
) {
    public static AlunoResponse de(Aluno aluno) {
        var dados = aluno.dadosAluno();

        var responsaveis = dados.responsaveis().stream()
                .map(contato -> new ResponsavelResponse(
                        contato.nome(),
                        contato.parentesco(),
                        contato.email(),
                        contato.telefone()
                ))
                .toList();

        return new AlunoResponse(
                aluno.id(),
                dados.nome(),
                dados.endereco(),
                dados.emailContato(),
                dados.telefoneContato(),
                aluno.ativo(),
                aluno.versao(),
                responsaveis
        );
    }

    public record ResponsavelResponse(
            String nome,
            String parentesco,
            String email,
            String telefone
    ) {
    }
}
