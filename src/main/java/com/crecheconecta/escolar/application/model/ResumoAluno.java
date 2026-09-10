package com.crecheconecta.escolar.application.model;

import java.util.UUID;

public record ResumoAluno(UUID id, String nome, boolean ativo, String versao) {
}
