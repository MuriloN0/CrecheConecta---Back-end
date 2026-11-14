package com.crecheconecta.service;

import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.model.DadosFichaSaude;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.FichaSaudeRepository;
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
 * Informações Relevantes (ficha de saúde).
 * Regra: o RESPONSAVEL do aluno escreve e lê; a DIRECAO lê de todos;
 * o PROFESSOR lê só de alunos matriculados numa turma em que ele leciona.
 */
@Service
public class FichaSaudeService {

    private final FichaSaudeRepository fichas;
    private final VinculoResponsavelService acessoResponsavel;
    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final TurmaProfessorRepository turmasProfessores;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public FichaSaudeService(FichaSaudeRepository fichas, VinculoResponsavelService acessoResponsavel, AlunoRepository alunos, UsuarioRepository usuarios, TurmaProfessorRepository turmasProfessores, CriptografiaService cripto, AuditoriaService auditoria, Clock clock) {
        this.fichas = fichas;
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
    public UUID cadastrar(UUID alunoId, DadosFichaSaude dados, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        ficha.setNome(dados.nome());
        ficha.setObservacoes(cripto.cifrar(dados.observacoes()));
        Instant agora = clock.instant();
        ficha.setDataCriacao(agora);
        ficha.setDataAtualizacao(agora);

        fichas.saveAndFlush(ficha);
        auditoria.aposCommit("FICHA_CADASTRADA", solicitante.id(), ficha.getId());
        return ficha.getId();
    }

    @Transactional
    public FichaSaudeResponse atualizar(UUID alunoId, UUID fichaId, DadosFichaSaude dados, Long versao, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        FichaSaude ficha = buscar(alunoId, fichaId);
        validarVersao(ficha, versao);

        ficha.setNome(dados.nome());
        ficha.setObservacoes(cripto.cifrar(dados.observacoes()));
        ficha.setDataAtualizacao(clock.instant());

        // flush agora: o Hibernate incrementa o @Version e a resposta já sai com a versão nova.
        fichas.saveAndFlush(ficha);

        auditoria.aposCommit("FICHA_EDITADA", solicitante.id(), ficha.getId());
        return FichaSaudeResponse.de(ficha, cripto.decifrar(ficha.getObservacoes()));
    }

    @Transactional
    public void excluir(UUID alunoId, UUID fichaId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        FichaSaude ficha = buscar(alunoId, fichaId);
        fichas.delete(ficha);
        fichas.flush();

        auditoria.aposCommit("FICHA_EXCLUIDA", solicitante.id(), fichaId);
    }

    // ---------- LEITURA: responsável, direção ou professor da turma ----------

    @Transactional(readOnly = true)
    public List<FichaSaudeResumoResponse> listar(UUID alunoId, UsuarioAtual solicitante) {
        exigirAcessoLeitura(solicitante, alunoId);

        List<FichaSaudeResumoResponse> lista = fichas.findByAlunoId(alunoId).stream().map(FichaSaudeResumoResponse::de).toList();

        auditoria.registrar("FICHAS_LISTADAS", solicitante.id(), alunoId);
        return lista;
    }

    @Transactional(readOnly = true)
    public FichaSaudeResponse visualizar(UUID alunoId, UUID fichaId, UsuarioAtual solicitante) {
        exigirAcessoLeitura(solicitante, alunoId);

        FichaSaude ficha = buscar(alunoId, fichaId);
        String observacoes = cripto.decifrar(ficha.getObservacoes());

        auditoria.registrar("FICHA_CONSULTADA", solicitante.id(), fichaId);
        return FichaSaudeResponse.de(ficha, observacoes);
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

    // Anti-IDOR de 2º nível: a ficha precisa pertencer ao aluno da URL.
    private FichaSaude buscar(UUID alunoId, UUID fichaId) {
        return fichas.findById(fichaId).filter(ficha -> ficha.getAlunoId().equals(alunoId)).orElseThrow(FichaSaudeNaoEncontradaException::new);
    }

    private void validarVersao(FichaSaude ficha, Long versao) {
        if (versao == null || !Objects.equals(ficha.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }

    private AccessDeniedException acessoNegado() {
        return new AccessDeniedException("Acesso negado às informações relevantes.");
    }
}
