package com.crecheconecta.service;

import com.crecheconecta.dto.InformacaoRelevanteResponse;
import com.crecheconecta.dto.InformacaoRelevanteResumoResponse;
import com.crecheconecta.entity.InformacaoRelevante;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.InformacaoRelevanteNaoEncontradaException;
import com.crecheconecta.model.DadosInformacaoRelevante;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.InformacaoRelevanteRepository;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Informações Relevantes (alergias, medicamentos e outros cuidados).
 * Regra: o RESPONSAVEL do aluno escreve e lê; a DIRECAO lê de todos;
 * o PROFESSOR lê só de alunos matriculados numa turma em que ele leciona.
 */
@Service
public class InformacaoRelevanteService {

    private final InformacaoRelevanteRepository informacoes;
    private final VinculoResponsavelService acessoResponsavel;
    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final TurmaProfessorRepository turmasProfessores;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public InformacaoRelevanteService(InformacaoRelevanteRepository informacoes, VinculoResponsavelService acessoResponsavel, AlunoRepository alunos, UsuarioRepository usuarios, TurmaProfessorRepository turmasProfessores, CriptografiaService cripto, AuditoriaService auditoria, Clock clock) {
        this.informacoes = informacoes;
        this.acessoResponsavel = acessoResponsavel;
        this.alunos = alunos;
        this.usuarios = usuarios;
        this.turmasProfessores = turmasProfessores;
        this.cripto = cripto;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------- ESCRITA: só o responsável do aluno ----------

    @Transactional
    public UUID cadastrar(UUID alunoId, DadosInformacaoRelevante dados, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        InformacaoRelevante informacao = new InformacaoRelevante();
        informacao.setAlunoId(alunoId);
        informacao.setNome(dados.nome());
        informacao.setObservacoes(cripto.cifrar(dados.observacoes()));
        Instant agora = clock.instant();
        informacao.setDataCriacao(agora);
        informacao.setDataAtualizacao(agora);

        informacoes.saveAndFlush(informacao);
        auditoria.aposCommit("INFORMACAO_RELEVANTE_CADASTRADA", solicitante.id(), informacao.getId());
        return informacao.getId();
    }

    @Transactional
    public InformacaoRelevanteResponse atualizar(UUID alunoId, UUID informacaoId, DadosInformacaoRelevante dados, Long versao, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        InformacaoRelevante informacao = buscar(alunoId, informacaoId);
        validarVersao(informacao, versao);

        informacao.setNome(dados.nome());
        informacao.setObservacoes(cripto.cifrar(dados.observacoes()));
        informacao.setDataAtualizacao(clock.instant());

        // flush agora: o Hibernate incrementa o @Version e a resposta já sai com a versão nova.
        informacoes.saveAndFlush(informacao);

        auditoria.aposCommit("INFORMACAO_RELEVANTE_EDITADA", solicitante.id(), informacao.getId());
        return InformacaoRelevanteResponse.de(informacao, cripto.decifrar(informacao.getObservacoes()));
    }

    @Transactional
    public void excluir(UUID alunoId, UUID informacaoId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        InformacaoRelevante informacao = buscar(alunoId, informacaoId);
        informacoes.delete(informacao);
        informacoes.flush();

        auditoria.aposCommit("INFORMACAO_RELEVANTE_EXCLUIDA", solicitante.id(), informacaoId);
    }

    // ---------- LEITURA: responsável, direção ou professor da turma ----------

    @Transactional(readOnly = true)
    public List<InformacaoRelevanteResumoResponse> listar(UUID alunoId, UsuarioAtual solicitante) {
        exigirAcessoLeitura(solicitante, alunoId);

        List<InformacaoRelevanteResumoResponse> lista = informacoes.findByAlunoId(alunoId).stream().map(InformacaoRelevanteResumoResponse::de).toList();

        auditoria.registrar("INFORMACOES_RELEVANTES_LISTADAS", solicitante.id(), alunoId);
        return lista;
    }

    @Transactional(readOnly = true)
    public InformacaoRelevanteResponse visualizar(UUID alunoId, UUID informacaoId, UsuarioAtual solicitante) {
        exigirAcessoLeitura(solicitante, alunoId);

        InformacaoRelevante informacao = buscar(alunoId, informacaoId);
        String observacoes = cripto.decifrar(informacao.getObservacoes());

        auditoria.registrar("INFORMACAO_RELEVANTE_CONSULTADA", solicitante.id(), informacaoId);
        return InformacaoRelevanteResponse.de(informacao, observacoes);
    }

    // ---------- regras de acesso ----------

    private void exigirAcessoLeitura(UsuarioAtual solicitante, UUID alunoId) {
        if (solicitante == null || solicitante.perfil() == null) {
            throw acessoNegado();
        }

        switch (solicitante.perfil()) {
            case RESPONSAVEL -> acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);
            case DIRECAO -> {
                exigirUsuarioAtivo(solicitante, Perfil.DIRECAO);
                exigirAlunoExistente(alunoId);
            }
            case PROFESSOR -> {
                exigirUsuarioAtivo(solicitante, Perfil.PROFESSOR);
                exigirProfessorDoAluno(solicitante, alunoId);
            }
        }
    }

    /** Re-checa no banco: o usuário existe, está ativo e tem mesmo o perfil do token. */
    private void exigirUsuarioAtivo(UsuarioAtual solicitante, Perfil perfilEsperado) {
        usuarios.findById(solicitante.id()).filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == perfilEsperado).orElseThrow(this::acessoNegado);
    }

    /** O aluno precisa estar matriculado numa turma em que o professor tem vínculo atual. Senão, 404 (anti-enumeração). */
    private void exigirProfessorDoAluno(UsuarioAtual professor, UUID alunoId) {
        boolean leciona = acessoResponsavel.turmaAtualDoAluno(alunoId).map(turmaId -> turmasProfessores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, professor.id())).orElse(false);

        if (!leciona) {
            throw new AlunoNaoEncontradoException();
        }
    }

    private void exigirAlunoExistente(UUID alunoId) {
        if (!alunos.existsById(alunoId)) {
            throw new AlunoNaoEncontradoException();
        }
    }

    // Anti-IDOR de 2º nível: a informação relevante precisa pertencer ao aluno da URL.
    private InformacaoRelevante buscar(UUID alunoId, UUID informacaoId) {
        return informacoes.findById(informacaoId).filter(informacao -> informacao.getAlunoId().equals(alunoId)).orElseThrow(InformacaoRelevanteNaoEncontradaException::new);
    }

    private void validarVersao(InformacaoRelevante informacao, Long versao) {
        if (versao == null || !Objects.equals(informacao.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }

    private AccessDeniedException acessoNegado() {
        return new AccessDeniedException("Acesso negado às informações relevantes.");
    }
}
