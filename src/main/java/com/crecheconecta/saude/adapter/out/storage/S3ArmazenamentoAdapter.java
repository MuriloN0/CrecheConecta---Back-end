package com.crecheconecta.saude.adapter.out.storage;

import com.crecheconecta.saude.application.port.out.ArmazenamentoArquivoPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class S3ArmazenamentoAdapter implements ArmazenamentoArquivoPort{

    @Override
    public String armazenar(String nomeArquivo, String tipoConteudo, byte[] conteudo) {
        String chave = "fichas-saude/" + UUID.randomUUID() + "/" + nomeArquivo;
        throw new UnsupportedOperationException(
                "S3ArmazenamentoAdapter.armazenar ainda nao implementado (chave sugerida: " + chave + "}");
    }
    @Override
    public void remover(String chaveArmazenamento) {
        throw new UnsupportedOperationException(
                "S3ArmazenamentoAdapter.remover ainda nao implementado");
    }
    @Override
    public byte[] baixar(String chaveArmazenamento) {
        throw new UnsupportedOperationException(
                "S3ArmazenamentoAdapter.baixar ainda nao implementado");
    }
}
