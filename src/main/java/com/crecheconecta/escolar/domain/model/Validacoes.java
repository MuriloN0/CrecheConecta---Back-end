package com.crecheconecta.escolar.domain.model;


import com.crecheconecta.escolar.domain.exception.RegraNegocioException;

import java.util.Locale;
import java.util.regex.Pattern;

final class Validacoes {
    private static final Pattern Email = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final Pattern Telefone = Pattern.compile("^[0-9]{10,15}$");

    private Validacoes() {}

    public static String textoObrigatorio(String valor, String campo, int tamanhoMaximo){
        String normalizado = textoOpcional(valor, campo, tamanhoMaximo);

        if (normalizado == null){
            throw new RegraNegocioException(campo + "é obrigatório");
        }
        return normalizado;
    }

    public static String textoOpcional(String valor, String campo, int tamanhoMaximo){
        if (valor == null || valor.isBlank()){return null;}

        String normalizado = valor.strip();

        if (normalizado.length() > tamanhoMaximo){
            throw new RegraNegocioException(
              campo + "deve ter até" + tamanhoMaximo + " caracteres"
            );
        }
        return normalizado;
    }

   public static String email(String valor, boolean obrigatorio){
        String normalizado = textoOpcional(valor, "E-mail", 254);

        if(normalizado == null){
            if(obrigatorio){
                throw new RegraNegocioException("E-mail é obregatório");
            }
            return null;
        }

        normalizado = normalizado.toLowerCase(Locale.ROOT);

        if (Email.matcher(normalizado).matches()){
            throw new RegraNegocioException("E-mail inválido");
        }

        return normalizado;
    }

    public static String telefone(String valor){
        if(valor == null){
            throw new RegraNegocioException("Telefone Obrigatório");
        }

        String normalizado = valor.replaceAll("[\\s()+.\\-]", "");

        if (!Telefone.matcher(normalizado).matches()){
            throw new RegraNegocioException("Telefone deve conter de 10 a 15 dígitos.");
        }
        return normalizado;
    }


}
