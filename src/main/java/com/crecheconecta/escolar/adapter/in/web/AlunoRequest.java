package com.crecheconecta.escolar.adapter.in.web;

import com.crecheconecta.escolar.domain.model.DadosAluno;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

import java.util.List;

public record AlunoRequest(
        @NotBlank @Size(max = 150)
        String nome,

        @Size(max = 300)
        String endereco,

        @NotBlank @Size(max = 254)
        String emailContato,

        @NotBlank @Size(max = 30)
        String telefoneContato,

        @NotNull @Size(max = 5)
        List<@NotNull @Valid ResponsavelRequest> responsaveis
) {
    public DadosAluno paraDominio() {
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
}
