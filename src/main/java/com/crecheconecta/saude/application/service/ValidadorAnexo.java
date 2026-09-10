package com.crecheconecta.saude.application.service;

import com.crecheconecta.saude.application.exception.FichaSaudeInvalidaException;
import com.crecheconecta.saude.application.port.in.CadastrarFichaSaudeUseCase.ArquivoUpload;

import java.util.List;
import java.util.Set;

public final class ValidadorAnexo {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ANEXOS = 10;
    private static final int MAX_NOME_ARQUIVO = 255;
    private static final Set<String> TIPOS_PERMITIDOS =
            Set.of("application/pdf", "image/png", "image/jpeg");

    private ValidadorAnexo() {
    }

    public static void validarQuantidade(int quantidadeTotal) {
        if (quantidadeTotal > MAX_ANEXOS) {
            throw new FichaSaudeInvalidaException(
                    "A ficha aceita no máximo " + MAX_ANEXOS + " anexos.");
        }
    }

    public static void validarArquivo(ArquivoUpload arquivo) {
        if (arquivo.nomeArquivo() == null || arquivo.nomeArquivo().isBlank()) {
            throw new FichaSaudeInvalidaException("Anexo sem nome de arquivo.");
            }
        if (arquivo.nomeArquivo().length() > MAX_NOME_ARQUIVO) {
            throw new FichaSaudeInvalidaException(
                    "O nome do arquivo deve ter até " + MAX_NOME_ARQUIVO + " caracteres.");
            }
        if (!TIPOS_PERMITIDOS.contains(arquivo.tipoConteudo())) {
            throw new FichaSaudeInvalidaException(
                    "Tipo de conteúdo não permitido: " + arquivo.tipoConteudo());
        }
        if (arquivo.conteudo() == null || arquivo.conteudo().length <= 0) {
            throw new FichaSaudeInvalidaException("Anexo vazio não é permitido.");
        }
        if (arquivo.conteudo().length > MAX_BYTES) {
            throw new FichaSaudeInvalidaException(
                    "O anexo excede o tamanho máximo de 10 MB.");
        }
    }
    public static void validarLote(List<ArquivoUpload> arquivos) {
        validarQuantidade(arquivos.size());
        arquivos.forEach(ValidadorAnexo::validarArquivo);
    }
}
