package com.crecheconecta.exception;

/**
 * O responsável ainda não aceitou a versão vigente dos Termos de Uso e da Política de Privacidade.
 * Sem esse aceite (LGPD), os dados da criança não são lidos nem gravados: a API responde 403 TERMO_PENDENTE
 * e o front leva o responsável para a tela de aceite.
 */
public class TermoPendenteException extends RuntimeException {

    public TermoPendenteException() {
        super("Aceite a versão vigente dos Termos de Uso e da Política de Privacidade para continuar.");
    }
}
