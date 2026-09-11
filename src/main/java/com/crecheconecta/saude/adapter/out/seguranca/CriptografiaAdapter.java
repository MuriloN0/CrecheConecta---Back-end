package com.crecheconecta.saude.adapter.out.seguranca;

import com.crecheconecta.saude.application.port.out.CriptografiaPort;
import org.springframework.stereotype.Component;

@Component
public class CriptografiaAdapter implements CriptografiaPort {

    @Override
    public String cifrar(String textoClaro) {
        throw new UnsupportedOperationException(
                "CriptografiaAdapter.cifrar ainda nao implementado");
    }

    @Override
    public String decifrar(String textoCifrado) {
        throw new UnsupportedOperationException(
                "CriptografiaAdapter.decifrar ainda nao implementado");
    }
}