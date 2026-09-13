package com.crecheconecta.saude.application.port.out;

public interface CriptografiaPort {
    String cifrar(String textoClaro);
    String decifrar(String textoCifrado);
}
