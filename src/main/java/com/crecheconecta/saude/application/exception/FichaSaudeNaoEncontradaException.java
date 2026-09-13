package com.crecheconecta.saude.application.exception;

import java.util.UUID;

public final class FichaSaudeNaoEncontradaException extends RuntimeException {
    public FichaSaudeNaoEncontradaException (UUID id) {
        super("Ficha de saúde não encontrado: " + id);
    }
}
