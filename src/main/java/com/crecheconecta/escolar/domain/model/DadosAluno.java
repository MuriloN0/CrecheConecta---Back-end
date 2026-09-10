package com.crecheconecta.escolar.domain.model;

import com.crecheconecta.escolar.domain.exception.RegraNegocioException;

import java.util.List;

public record DadosAluno(String nome, String emailContato, String telefoneContato, String endereco, List<ContatoResponsavel> responsaveis) {
    public DadosAluno {
        nome = Validacoes.textoObrigatorio(nome, "Nome do aluno", 150);
        endereco = Validacoes.textoObrigatorio(endereco, "ENdereço", 300);
        emailContato = Validacoes.email(emailContato, true);
        telefoneContato = Validacoes.telefone(telefoneContato);

        if (responsaveis == null){
            throw new RegraNegocioException("Os respnsáveis devem ser informados.");
        }

        if (responsaveis.size() > 5) {
            throw new RegraNegocioException("O aluno pode ter no máximo 5 responsáveis.");
        }

        if (responsaveis.stream().anyMatch(responsavel -> responsavel == null)){
            throw new RegraNegocioException("A lista não pode conter responsável nulo");
        }
        responsaveis = List.copyOf(responsaveis);
    }
}
