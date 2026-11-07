package com.crecheconecta.exception;

public class ItemMalaNaoEncontradoException extends RuntimeException {

    public ItemMalaNaoEncontradoException() {

        super("Item da mala não encontrado.");
    }
}
