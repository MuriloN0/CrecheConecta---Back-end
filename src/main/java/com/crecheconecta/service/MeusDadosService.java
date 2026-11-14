package com.crecheconecta.service;

import com.crecheconecta.dto.MeusDadosResponse;
import com.crecheconecta.dto.MeusDadosResponse.AceiteRegistrado;
import com.crecheconecta.dto.MeusDadosResponse.DadosFilho;
import com.crecheconecta.dto.MeusDadosResponse.DiarioExportado;
import com.crecheconecta.dto.MeusDadosResponse.InformacaoExportada;
import com.crecheconecta.dto.MeusDadosResponse.ObservacaoExportada;
import com.crecheconecta.dto.MeusDadosResponse.PresencaExportada;
import com.crecheconecta.dto.MeusDadosResponse.Titular;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.repository.AceiteTermoRepository;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.DiarioRotinaRepository;
import com.crecheconecta.repository.InformacaoRelevanteRepository;
import com.crecheconecta.repository.ObservacaoRepository;
import com.crecheconecta.repository.PresencaRepository;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * LGPD: o responsável baixa uma cópia dos dados dele e das crianças vinculadas.
 * De propósito NÃO passa pelo VinculoResponsavelService: o acesso aos próprios dados é um direito
 * do titular e não depende de ele aceitar uma nova versão dos termos.
 */
@Service
public class MeusDadosService {

    private final UsuarioRepository usuarios;
    private final AceiteTermoRepository aceites;
    private final AlunoResponsavelRepository vinculos;
    private final AlunoRepository alunos;
    private final VinculoResponsavelService acessoResponsavel;
    private final TurmaRepository turmas;
    private final InformacaoRelevanteRepository informacoes;
    private final ObservacaoRepository observacoes;
    private final DiarioRotinaRepository diarios;
    private final PresencaRepository presencas;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public MeusDadosService(UsuarioRepository usuarios, AceiteTermoRepository aceites, AlunoResponsavelRepository vinculos, AlunoRepository alunos, VinculoResponsavelService acessoResponsavel, TurmaRepository turmas, InformacaoRelevanteRepository informacoes, ObservacaoRepository observacoes, DiarioRotinaRepository diarios, PresencaRepository presencas, CriptografiaService cripto, AuditoriaService auditoria, Clock clock) {
        this.usuarios = usuarios;
        this.aceites = aceites;
        this.vinculos = vinculos;
        this.alunos = alunos;
        this.acessoResponsavel = acessoResponsavel;
        this.turmas = turmas;
        this.informacoes = informacoes;
        this.observacoes = observacoes;
        this.diarios = diarios;
        this.presencas = presencas;
        this.cripto = cripto;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeusDadosResponse exportar(UsuarioAtual solicitante) {
        Usuario titular = exigirResponsavelAtivo(solicitante);

        var aceitesDoTitular = aceites.findByUsuarioIdOrderByDataAceiteDesc(titular.getId()).stream()
                .map(aceite -> new AceiteRegistrado(aceite.getVersaoTermo(), aceite.getDataAceite()))
                .toList();

        var alunoIds = vinculos.findByResponsavelIdAndEncerradoEmIsNull(titular.getId()).stream().map(AlunoResponsavel::getAlunoId).toList();
        var filhos = alunos.findAllById(alunoIds).stream()
                .sorted(Comparator.comparing(Aluno::getNome))
                .map(this::dadosDoFilho)
                .toList();

        auditoria.registrar("DADOS_EXPORTADOS", titular.getId(), null);
        return new MeusDadosResponse(
                clock.instant(),
                new Titular(titular.getNome(), titular.getEmail(), titular.getTelefone()),
                aceitesDoTitular,
                filhos);
    }

    private DadosFilho dadosDoFilho(Aluno aluno) {
        UUID alunoId = aluno.getId();
        String turma = acessoResponsavel.turmaAtualDoAluno(alunoId).flatMap(turmas::findById).map(Turma::getNome).orElse(null);

        var informacoesDoFilho = informacoes.findByAlunoId(alunoId).stream()
                .map(informacao -> new InformacaoExportada(informacao.getNome(), cripto.decifrar(informacao.getObservacoes()), informacao.getDataCriacao()))
                .toList();

        var observacoesDoFilho = observacoes.findByAlunoIdOrderByDataCriacaoDesc(alunoId).stream()
                .map(obs -> new ObservacaoExportada(obs.getTitulo(), cripto.decifrar(obs.getConteudo()), nomeDoUsuario(obs.getAutorId()), obs.getDataCriacao()))
                .toList();

        var diariosDoFilho = diarios.findByAlunoIdOrderByDataDesc(alunoId).stream()
                .map(diario -> new DiarioExportado(diario.getData(), diario.getAlimentacao().name(), diario.getSono().name(), diario.getHumor().name(), diario.getHigiene() == null ? null : diario.getHigiene().name(), diario.isEvacuacao(), diario.getObservacaoProfessor()))
                .toList();

        // Presença ainda pendente (sem marcação) não é um dado registrado: fica de fora.
        var frequencia = presencas.findByAlunoIdOrderByDataAsc(alunoId).stream()
                .filter(presenca -> presenca.getPresente() != null)
                .map(presenca -> new PresencaExportada(presenca.getData(), presenca.getPresente()))
                .toList();

        return new DadosFilho(aluno.getNome(), aluno.getDataNascimento(), aluno.getAnoNascimento(), turma, informacoesDoFilho, observacoesDoFilho, diariosDoFilho, frequencia);
    }

    private String nomeDoUsuario(UUID usuarioId) {
        return usuarioId == null ? null : usuarios.findById(usuarioId).map(Usuario::getNome).orElse(null);
    }

    private Usuario exigirResponsavelAtivo(UsuarioAtual solicitante) {
        if (solicitante == null || solicitante.id() == null || solicitante.perfil() != Perfil.RESPONSAVEL) {
            throw acessoNegado();
        }
        return usuarios.findById(solicitante.id())
                .filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == Perfil.RESPONSAVEL)
                .orElseThrow(this::acessoNegado);
    }

    private AccessDeniedException acessoNegado() {
        return new AccessDeniedException("A exportação de dados é feita pelo próprio responsável.");
    }
}
