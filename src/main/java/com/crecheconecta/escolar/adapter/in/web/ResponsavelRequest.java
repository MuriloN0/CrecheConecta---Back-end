package com.crecheconecta.escolar.adapter.in.web;

import com.crecheconecta.escolar.domain.model.ContatoResponsavel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponsavelRequest(@NotBlank @Size(max = 150)
                                  String nome,

                                 @NotBlank @Size(max = 50)
                                  String parentesco,

                                 @Size(max = 254)
                                  String email,

                                 @NotBlank @Size(max = 30)
                                  String telefone)
{
    public ContatoResponsavel paraDominio() {
        return new ContatoResponsavel(nome, parentesco, email, telefone);
    }
}
