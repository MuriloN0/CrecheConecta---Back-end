package com.crecheconecta.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * LGPD (art. 18, II e V): cópia dos dados que o sistema guarda sobre o responsável e as crianças
 * vinculadas a ele, num formato que ele pode baixar (JSON).
 */
public record MeusDadosResponse(
        Instant geradoEm,
        Titular titular,
        List<AceiteRegistrado> aceitesDosTermos,
        List<DadosFilho> filhos) {

    public record Titular(String nome, String email, String telefone) {
    }

    public record AceiteRegistrado(String versao, Instant aceitoEm) {
    }

    public record DadosFilho(
            String nome,
            LocalDate dataNascimento,
            Integer anoNascimento,
            String turma,
            List<InformacaoExportada> informacoesRelevantes,
            List<ObservacaoExportada> observacoesDosProfessores,
            List<DiarioExportado> diarios,
            List<PresencaExportada> frequencia) {
    }

    public record InformacaoExportada(String nome, String observacoes, Instant cadastradaEm) {
    }

    public record ObservacaoExportada(String titulo, String conteudo, String professor, Instant registradaEm) {
    }

    public record DiarioExportado(LocalDate data, String alimentacao, String sono, String humor, String higiene, boolean evacuacao, String observacaoProfessor) {
    }

    public record PresencaExportada(LocalDate data, boolean presente) {
    }
}
