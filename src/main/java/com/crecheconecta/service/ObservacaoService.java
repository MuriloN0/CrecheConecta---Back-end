package com.crecheconecta.service;

import com.crecheconecta.dto.ObservacaoResponse;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.Observacao;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.ObservacaoNaoEncontradaException;
import com.crecheconecta.model.DadosObservacao;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.ObservacaoRepository;
import com.crecheconecta.repository.TurmaProfessorRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Observações do aluno.
 * Escreve: o PROFESSOR vinculado à turma atual do aluno (só o autor edita ou exclui).
 * Lê: o RESPONSAVEL do aluno, o PROFESSOR da turma e a DIRECAO.
 * O conteúdo fica cifrado no banco (AES-GCM), como nas informações relevantes.
 */
@Service
public class ObservacaoService {

    private final ObservacaoRepository observacoes;
    private final VinculoResponsavelService acessoResponsavel;
    private final TurmaProfessorRepository turmasProfessores;
    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ObservacaoService(ObservacaoRepository observacoes, VinculoResponsavelService acessoResponsavel, TurmaProfessorRepository turmasProfessores, AlunoRepository alunos, UsuarioRepository usuarios, CriptografiaService cripto, AuditoriaService auditoria, Clock clock) {
        this.observacoes = observacoes;
        this.acessoResponsavel = acessoResponsavel;
        this.turmasProfessores = turmasProfessores;
        this.alunos = alunos;
        this.usuarios = usuarios;
        this.cripto = cripto;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public UUID cadastrar(UUID alunoId, DadosObservacao dados, UsuarioAtual solicitante) {
        exigirProfessorDoAluno(solicitante, alunoId);

        Observacao obs = new Observacao();
        obs.setAlunoId(alunoId);
        obs.setAutorId(solicitante.id());
        obs.setTitulo(dados.titulo());
        obs.setConteudo(cripto.cifrar(dados.conteudo()));
        Instant agora = clock.instant();
        obs.setDataCriacao(agora);
        obs.setDataAtualizacao(agora);

        observacoes.saveAndFlush(obs);
        auditoria.aposCommit("OBSERVACAO_CADASTRADA", solicitante.id(), obs.getId());
        return obs.getId();
    }

    @Transactional(readOnly = true)
    public List<ObservacaoResponse> listar(UUID alunoId, UsuarioAtual solicitante) {
        exigirAcessoLeitura(solicitante, alunoId);

        List<Observacao> lista = observacoes.findByAlunoIdOrderByDataCriacaoDesc(alunoId);
        Map<UUID, String> nomes = nomesDosAutores(lista);

        List<ObservacaoResponse> resposta = lista.stream().map(obs -> resposta(obs, nomes.get(obs.getAutorId()), solicitante)).toList();

        auditoria.registrar("OBSERVACOES_LISTADAS", solicitante.id(), alunoId);
        return resposta;
    }

    @Transactional(readOnly = true)
    public ObservacaoResponse visualizar(UUID alunoId, UUID observacaoId, UsuarioAtual solicitante) {
        exigirAcessoLeitura(solicitante, alunoId);

        Observacao obs = buscar(alunoId, observacaoId);

        auditoria.registrar("OBSERVACAO_CONSULTADA", solicitante.id(), observacaoId);
        return resposta(obs, nomeDoAutor(obs.getAutorId()), solicitante);
    }

    @Transactional
    public ObservacaoResponse atualizar(UUID alunoId, UUID observacaoId, DadosObservacao dados, Long versao, UsuarioAtual solicitante) {
        exigirProfessorDoAluno(solicitante, alunoId);

        Observacao obs = buscar(alunoId, observacaoId);
        exigirAutor(obs, solicitante);
        validarVersao(obs, versao);

        obs.setTitulo(dados.titulo());
        obs.setConteudo(cripto.cifrar(dados.conteudo()));
        obs.setDataAtualizacao(clock.instant());

        // flush agora: o Hibernate incrementa o @Version e a resposta já sai com a versão NOVA.
        observacoes.saveAndFlush(obs);

        auditoria.aposCommit("OBSERVACAO_EDITADA", solicitante.id(), obs.getId());
        return resposta(obs, nomeDoAutor(obs.getAutorId()), solicitante);
    }

    @Transactional
    public void excluir(UUID alunoId, UUID observacaoId, UsuarioAtual solicitante) {
        exigirProfessorDoAluno(solicitante, alunoId);

        Observacao obs = buscar(alunoId, observacaoId);
        exigirAutor(obs, solicitante);

        observacoes.delete(obs);
        observacoes.flush();

        auditoria.aposCommit("OBSERVACAO_EXCLUIDA", solicitante.id(), observacaoId);
    }

    // ---------- regras de acesso ----------

    private void exigirAcessoLeitura(UsuarioAtual solicitante, UUID alunoId) {
        if (solicitante == null || solicitante.perfil() == null) {
            throw acessoNegado();
        }

        switch (solicitante.perfil()) {
            case RESPONSAVEL -> acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);
            case PROFESSOR -> exigirProfessorDoAluno(solicitante, alunoId);
            case DIRECAO -> {
                exigirUsuarioAtivo(solicitante, Perfil.DIRECAO);
                if (alunoId == null || !alunos.existsById(alunoId)) {
                    throw new AlunoNaoEncontradoException();
                }
            }
        }
    }

    /**
     * Professor ativo e aprovado, vinculado à turma em que o aluno (ativo) está matriculado hoje.
     * Se não lecionar para o aluno, responde 404 (e não 403) para não revelar que o aluno existe.
     */
    private void exigirProfessorDoAluno(UsuarioAtual solicitante, UUID alunoId) {
        exigirUsuarioAtivo(solicitante, Perfil.PROFESSOR);

        boolean alunoAtivo = alunoId != null && alunos.findById(alunoId).filter(Aluno::isAtivo).isPresent();
        boolean leciona = alunoAtivo && acessoResponsavel.turmaAtualDoAluno(alunoId)
                .map(turmaId -> turmasProfessores.existsByTurmaIdAndProfessorIdAndEncerradoEmIsNull(turmaId, solicitante.id()))
                .orElse(false);

        if (!leciona) {
            throw new AlunoNaoEncontradoException();
        }
    }

    /** Re-checa no banco: o usuário existe, está ativo, aprovado e tem mesmo o perfil do token. */
    private void exigirUsuarioAtivo(UsuarioAtual solicitante, Perfil perfilEsperado) {
        if (solicitante == null || solicitante.id() == null || solicitante.perfil() != perfilEsperado) {
            throw acessoNegado();
        }

        usuarios.findById(solicitante.id())
                .filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == perfilEsperado && usuario.getSituacaoCadastro() == SituacaoCadastro.APROVADO)
                .orElseThrow(this::acessoNegado);
    }

    private void exigirAutor(Observacao obs, UsuarioAtual solicitante) {
        if (!Objects.equals(obs.getAutorId(), solicitante.id())) {
            throw new AccessDeniedException("Somente o professor que registrou a observação pode alterá-la.");
        }
    }

    // ---------- apoio ----------

    // Anti-IDOR de 2º nível: a observação precisa pertencer ao aluno da URL.
    private Observacao buscar(UUID alunoId, UUID observacaoId) {
        return observacoes.findById(observacaoId).filter(obs -> obs.getAlunoId().equals(alunoId)).orElseThrow(ObservacaoNaoEncontradaException::new);
    }

    private void validarVersao(Observacao obs, Long versao) {
        if (versao == null || !Objects.equals(obs.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }

    private ObservacaoResponse resposta(Observacao obs, String autorNome, UsuarioAtual solicitante) {
        boolean editavel = Objects.equals(obs.getAutorId(), solicitante.id());
        return ObservacaoResponse.de(obs, cripto.decifrar(obs.getConteudo()), autorNome, editavel);
    }

    /** Uma consulta só para os nomes de todos os autores da lista. */
    private Map<UUID, String> nomesDosAutores(List<Observacao> lista) {
        Set<UUID> ids = lista.stream().map(Observacao::getAutorId).collect(Collectors.toSet());
        Map<UUID, String> nomes = new HashMap<>();
        usuarios.findAllById(ids).forEach(usuario -> nomes.put(usuario.getId(), usuario.getNome()));
        return nomes;
    }

    private String nomeDoAutor(UUID autorId) {
        return usuarios.findById(autorId).map(Usuario::getNome).orElse(null);
    }

    private AccessDeniedException acessoNegado() {
        return new AccessDeniedException("Acesso negado às observações do aluno.");
    }
}
