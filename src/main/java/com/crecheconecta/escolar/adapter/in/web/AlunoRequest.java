package com.crecheconecta.escolar.adapter.in.web;

import com.crecheconecta.escolar.domain.model.ContatoResponsavel;
import com.crecheconecta.escolar.domain.model.DadosAluno;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AlunoRequest() {
    @NotBlank
    @Size(max = 150)
    public static String nome;

    @Size(max = 300)
    public static String endereco;

    @NotBlank
    @Size(max = 254)
    public static String emailContato;

    @NotBlank
    @Size(max = 30)
    public static String telefoneContato;

    @NotNull
    @Size(max = 5)
    public static List<@NotNull @Valid ResponsavelRequest> responsaveis;
 {

        DadosAluno paraDominio() {
            return new DadosAluno(
                    nome,
                    endereco,
                    emailContato,
                    telefoneContato,
                    responsaveis.stream()
                            .map(ResponsavelRequest::paraDominio)
                            .toList()
            );
        }

        record ResponsavelRequest(
                @NotBlank
                @Size(max = 150)
                String nome,

                @NotBlank
                @Size(max = 50)
                String parentesco,

                @Size(max = 254)
                String email,

                @NotBlank
                @Size(max = 30)
                String telefone
        ) {

            public ContatoResponsavel paraDominio() {
                return new ContatoResponsavel(
                        nome,
                        parentesco,
                        email,
                        telefone
                );
            }
        }
}
