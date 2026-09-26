package com.crecheconecta.security;

import com.crecheconecta.entity.Perfil;

import java.util.UUID;

public record UsuarioAutenticado(UUID usuarioId, UUID sessaoId, String nome, String email, Perfil perfil) {
}
