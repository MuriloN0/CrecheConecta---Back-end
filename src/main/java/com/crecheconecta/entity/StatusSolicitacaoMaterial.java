package com.crecheconecta.entity;

public enum StatusSolicitacaoMaterial {
    ABERTA,
    ATENDIDA,
    RECUSADA,
    CANCELADA;

    public boolean isFinalizada() {

        return this != ABERTA;
    }
}
