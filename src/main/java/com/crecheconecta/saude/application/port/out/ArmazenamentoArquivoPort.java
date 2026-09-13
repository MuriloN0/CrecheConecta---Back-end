package com.crecheconecta.saude.application.port.out;

public interface ArmazenamentoArquivoPort {
    String armazenar(String nomeArquivo, String tipoConteudo, byte[] conteudo);
    void remover(String chaveArmazenamento);
    byte[] baixar(String chaveArmazenamento);
}
