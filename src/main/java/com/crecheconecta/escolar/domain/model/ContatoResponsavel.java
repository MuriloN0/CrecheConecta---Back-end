package com.crecheconecta.escolar.domain.model;

public record ContatoResponsavel(String nome, String parentesco, String email, String telefone){

    public ContatoResponsavel{
        nome = Validacoes.textoObrigatorio(nome, "Nome do Responsável", 150);

        parentesco = Validacoes.textoObrigatorio(parentesco, "Parentesco", 50);

        email = Validacoes.email(email, false);
        telefone = Validacoes.telefone(telefone);
    }

}
