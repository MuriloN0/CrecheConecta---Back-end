package com.crecheconecta.security;

public class RateLimitException extends RuntimeException {

    private final long segundosRestantes;

    public RateLimitException(long segundosRestantes) {

        super("Muitas solicitações. Aguarde antes de tentar novamente.");
        this.segundosRestantes = Math.max(1, segundosRestantes);
    }

    public long getSegundosRestantes() {

        return segundosRestantes;
    }
}