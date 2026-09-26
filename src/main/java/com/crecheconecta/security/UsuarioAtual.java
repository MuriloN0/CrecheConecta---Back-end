package com.crecheconecta.security;

import java.util.UUID;

public record UsuarioAtual(UUID id, Perfil perfil) {}