package com.crecheconecta.dto;

import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;

import java.util.UUID;

public record ProfessorResponse(UUID id, String nome, String email, String telefone, boolean ativo, SituacaoCadastro situacaoCadastro, Long versao) {

    public static ProfessorResponse de(Usuario usuario) {

        return new ProfessorResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone(), usuario.isAtivo(), usuario.getSituacaoCadastro(), usuario.getVersao());
    }
}