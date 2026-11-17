package com.crecheconecta.service;

import com.crecheconecta.exception.RegraNegocioException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class FiltroLinguagem {

    private static final Pattern MARCAS_E_CARACTERES_INVISIVEIS =
            Pattern.compile("[\\p{M}\\p{Cf}]+");

    private static final Pattern PALAVRA = Pattern.compile("[\\p{L}\\p{N}]+");

    private static final Pattern EXPRESSAO_OFENSIVA = Pattern.compile(
            "(?<![\\p{L}\\p{N}])vai[^\\p{L}\\p{N}]+tomar[^\\p{L}\\p{N}]+no[^\\p{L}\\p{N}]+cu(?![\\p{L}\\p{N}])"
    );

    private static final Set<String> TERMOS_BLOQUEADOS_EM_NOMES = Set.of(
            "arrombado", "arrombada", "caralho", "caralhos",
            "imbecil", "imbecis", "merda", "merdas",
            "porra", "puta", "putas", "desgracado", "desgracada"
    );

    private static final Set<String> TERMOS_ADICIONAIS_DO_CHAT = Set.of(
            "arrombados", "arrombadas", "babaca", "babacas",
            "desgracados", "desgracadas", "foda", "fodase", "foder",
            "idiota", "idiotas", "otario", "otaria", "otarios", "otarias",
            "puto", "putos", "vagabundo", "vagabunda", "vagabundos", "vagabundas"
    );

    private FiltroLinguagem() {}

    public static void validarMensagem(String mensagem) {
        String texto = normalizar(mensagem);

        if (contemTermoBloqueado(texto, TERMOS_BLOQUEADOS_EM_NOMES)
                || contemTermoBloqueado(texto, TERMOS_ADICIONAIS_DO_CHAT)
                || EXPRESSAO_OFENSIVA.matcher(texto).find()) {
            throw new RegraNegocioException(
                    "A mensagem contém linguagem ofensiva. Revise o texto antes de enviar."
            );
        }
    }

    public static void validarNomeResponsavel(String nome) {
        validarNome(nome, "Revise o nome do responsável. Ele contém um termo não permitido.");
    }

    public static void validarNomeAluno(String nome) {
        validarNome(nome, "Revise o nome do aluno. Ele contém um termo não permitido.");
    }

    private static void validarNome(String nome, String mensagem) {
        String texto = normalizar(nome);

        if (contemTermoBloqueado(texto, TERMOS_BLOQUEADOS_EM_NOMES)
                || EXPRESSAO_OFENSIVA.matcher(texto).find()) {
            throw new RegraNegocioException(mensagem);
        }
    }

    private static boolean contemTermoBloqueado(String texto, Set<String> bloqueados) {
        var palavras = PALAVRA.matcher(texto);

        while (palavras.find()) {
            if (bloqueados.contains(palavras.group())) {
                return true;
            }
        }

        return false;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }

        String decomposto = Normalizer.normalize(texto, Normalizer.Form.NFKD);
        return MARCAS_E_CARACTERES_INVISIVEIS.matcher(decomposto)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}