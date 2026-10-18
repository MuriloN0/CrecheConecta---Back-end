package com.crecheconecta.service;

import com.crecheconecta.dto.ObservacaoResponse;
import com.crecheconecta.entity.Observacao;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.ObservacaoNaoEncontradaException;
import com.crecheconecta.model.DadosObservacao;
import com.crecheconecta.repository.ObservacaoRepository;
import com.crecheconecta.security.Perfil;
import com.crecheconecta.security.UsuarioAtual;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ObservacaoService {

    private final ObservacaoRepository repository;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ObservacaoService(
            ObservacaoRepository repository,
            CriptografiaService cripto,
            AuditoriaService auditoria,
            Clock clock) {
        this.repository = repository;
        this.cripto = cripto;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---- CADASTRAR ----
    public UUID cadastrar(UUID alunoId, DadosObservacao dados, UsuarioAtual usuario) {
        exigirAutenticado(usuario);

        Observacao obs = new Observacao();
        obs.setAlunoId(alunoId);
        obs.setTitulo(dados.titulo());
        obs.setConteudo(cripto.cifrar(dados.conteudo()));
        Instant agora = clock.instant();
        obs.setDataCriacao(agora);
        obs.setDataAtualizacao(agora);

        repository.save(obs);
        auditoria.aposCommit("OBSERVACAO_CADASTRADA", usuario.id(), obs.getId());
        return obs.getId();
    }

    @Transactional(readOnly = true)
    public List<ObservacaoResponse> listar(UUID alunoId, UsuarioAtual usuario) {
        exigirAutenticado(usuario);
        List<ObservacaoResponse> lista =
                repository.findByAlunoId(alunoId).stream()
                        .map(obs -> ObservacaoResponse.de(obs, cripto.decifrar(obs.getConteudo())))
                        .toList();
        auditoria.registrar("OBSERVACOES_LISTADAS", usuario.id(), alunoId);
        return lista;
    }

    @Transactional(readOnly = true)
    public ObservacaoResponse visualizar(UUID alunoId, UUID observacaoId, UsuarioAtual usuario) {
        exigirAutenticado(usuario);
        Observacao obs = buscar(alunoId, observacaoId);
        String conteudo = cripto.decifrar(obs.getConteudo());
        auditoria.registrar("OBSERVACAO_CONSULTADA", usuario.id(), observacaoId);
        return ObservacaoResponse.de(obs, conteudo);
    }

    public ObservacaoResponse atualizar(
            UUID alunoId, UUID observacaoId, DadosObservacao dados, Long versao, UsuarioAtual usuario) {
        exigirAutenticado(usuario);
        Observacao obs = buscar(alunoId, observacaoId);
        validarVersao(obs, versao);

        obs.setTitulo(dados.titulo());
        obs.setConteudo(cripto.cifrar(dados.conteudo()));
        obs.setDataAtualizacao(clock.instant());

        auditoria.aposCommit("OBSERVACAO_EDITADA", usuario.id(), obs.getId());
        String conteudo = cripto.decifrar(obs.getConteudo());
        return ObservacaoResponse.de(obs, conteudo);
    }

    public void excluir(UUID alunoId, UUID observacaoId, UsuarioAtual usuario) {
        exigirAutenticado(usuario);
        Observacao obs = buscar(alunoId, observacaoId);
        repository.delete(obs);
        auditoria.aposCommit("OBSERVACAO_EXCLUIDA", usuario.id(), observacaoId);
    }

    private Observacao buscar(UUID alunoId, UUID observacaoId) {
        Observacao obs =
                repository.findById(observacaoId).orElseThrow(ObservacaoNaoEncontradaException::new);
        if (!obs.getAlunoId().equals(alunoId)) {
            throw new ObservacaoNaoEncontradaException();
        }
        return obs;
    }

    private void exigirAutenticado(UsuarioAtual usuario) {
        if (usuario == null || usuario.perfil() == null) {
            throw new AcessoNegadoException("Usuario nao autenticado.");
        }
    }

    private void validarVersao(Observacao obs, Long versao) {
        if (versao == null || !Objects.equals(obs.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }
}
