package com.crecheconecta.service;

import com.crecheconecta.dto.ObservacaoResponse;
import com.crecheconecta.entity.Observacao;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.ObservacaoNaoEncontradaException;
import com.crecheconecta.model.DadosObservacao;
import com.crecheconecta.repository.ObservacaoRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Observações do filho (CRUD do RESPONSAVEL).
 * Toda operação passa pelo "porteiro": só o responsável ATUAL do aluno lê e escreve.
 */
@Service
public class ObservacaoService {

    private final ObservacaoRepository observacoes;
    private final VinculoResponsavelService acessoResponsavel;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ObservacaoService(ObservacaoRepository observacoes, VinculoResponsavelService acessoResponsavel, CriptografiaService cripto, AuditoriaService auditoria, Clock clock) {
        this.observacoes = observacoes;
        this.acessoResponsavel = acessoResponsavel;
        this.cripto = cripto;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public UUID cadastrar(UUID alunoId, DadosObservacao dados, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        Observacao obs = new Observacao();
        obs.setAlunoId(alunoId);
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
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        List<ObservacaoResponse> lista = observacoes.findByAlunoId(alunoId).stream().map(obs -> ObservacaoResponse.de(obs, cripto.decifrar(obs.getConteudo()))).toList();

        auditoria.registrar("OBSERVACOES_LISTADAS", solicitante.id(), alunoId);
        return lista;
    }

    @Transactional(readOnly = true)
    public ObservacaoResponse visualizar(UUID alunoId, UUID observacaoId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        Observacao obs = buscar(alunoId, observacaoId);
        String conteudo = cripto.decifrar(obs.getConteudo());

        auditoria.registrar("OBSERVACAO_CONSULTADA", solicitante.id(), observacaoId);
        return ObservacaoResponse.de(obs, conteudo);
    }

    @Transactional
    public ObservacaoResponse atualizar(UUID alunoId, UUID observacaoId, DadosObservacao dados, Long versao, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        Observacao obs = buscar(alunoId, observacaoId);
        validarVersao(obs, versao);

        obs.setTitulo(dados.titulo());
        obs.setConteudo(cripto.cifrar(dados.conteudo()));
        obs.setDataAtualizacao(clock.instant());

        // flush agora: o Hibernate incrementa o @Version e a resposta já sai com a versão NOVA.
        observacoes.saveAndFlush(obs);

        auditoria.aposCommit("OBSERVACAO_EDITADA", solicitante.id(), obs.getId());
        return ObservacaoResponse.de(obs, cripto.decifrar(obs.getConteudo()));
    }

    @Transactional
    public void excluir(UUID alunoId, UUID observacaoId, UsuarioAtual solicitante) {
        acessoResponsavel.exigirResponsavelDoAluno(solicitante, alunoId);

        Observacao obs = buscar(alunoId, observacaoId);
        observacoes.delete(obs);
        observacoes.flush();

        auditoria.aposCommit("OBSERVACAO_EXCLUIDA", solicitante.id(), observacaoId);
    }

    // Anti-IDOR de 2º nível: a observação precisa pertencer ao aluno da URL.
    private Observacao buscar(UUID alunoId, UUID observacaoId) {
        return observacoes.findById(observacaoId).filter(obs -> obs.getAlunoId().equals(alunoId)).orElseThrow(ObservacaoNaoEncontradaException::new);
    }

    private void validarVersao(Observacao obs, Long versao) {
        if (versao == null || !Objects.equals(obs.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }
}
