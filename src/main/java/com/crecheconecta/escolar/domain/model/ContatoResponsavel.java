package com.crecheconecta.escolar.domain.model;

public record ContatoResponsavel( String nome, String email, String telefone, String parentesco){

    public ContatoResponsavel{
        nome = Validacoes.textoObrigatorio(nome, "Nome do Responsável", 150);

        parentesco = Validacoes.textoObrigatorio(parentesco, "Parentesco", 50);

        email = Validacoes.email(email, false);
        telefone = Validacoes.telefone(telefone);
    }

}
