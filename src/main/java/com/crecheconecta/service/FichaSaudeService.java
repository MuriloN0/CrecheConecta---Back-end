package com.crecheconecta.service;

import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.model.DadosFichaSaude;
import com.crecheconecta.repository.FichaSaudeRepository;
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
public class FichaSaudeService {

    private final FichaSaudeRepository repository;
    private final CriptografiaService cripto;
    private final Clock clock;

    public FichaSaudeService(
            FichaSaudeRepository repository, CriptografiaService cripto, Clock clock) {
        this.repository = repository;
        this.cripto = cripto;
        this.clock = clock;
    }

    // ---- CADASTRAR ----
    public UUID cadastrar(UUID alunoId, DadosFichaSaude dados, UsuarioAtual usuario) {
        exigirGestao(usuario);

        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        ficha.setNome(dados.nome());
        ficha.setObservacoes(cripto.cifrar(dados.observacoes()));
        Instant agora = clock.instant();
        ficha.setDataCriacao(agora);
        ficha.setDataAtualizacao(agora);

        repository.save(ficha);
        return ficha.getId();
    }

    // ---- LISTAR (resumo, sem observações) ----
    @Transactional(readOnly = true)
    public List<FichaSaudeResumoResponse> listar(UUID alunoId, UsuarioAtual usuario) {
        exigirAcessoLeitura(usuario, alunoId);
        return repository.findByAlunoId(alunoId).stream()
                .map(FichaSaudeResumoResponse::de)
                .toList();
    }

    // ---- VISUALIZAR (detalhe, com observação decifrada) ----
    @Transactional(readOnly = true)
    public FichaSaudeResponse visualizar(UUID alunoId, UUID fichaId, UsuarioAtual usuario) {
        exigirAcessoLeitura(usuario, alunoId);
        FichaSaude ficha = buscar(alunoId, fichaId);
        String obs = cripto.decifrar(ficha.getObservacoes());
        return FichaSaudeResponse.de(ficha, obs);
    }

    // ---- ATUALIZAR ----
    public FichaSaudeResponse atualizar(
            UUID alunoId, UUID fichaId, DadosFichaSaude dados, Long versao, UsuarioAtual usuario) {
        exigirGestao(usuario);
        FichaSaude ficha = buscar(alunoId, fichaId);
        validarVersao(ficha, versao);

        ficha.setNome(dados.nome());
        ficha.setObservacoes(cripto.cifrar(dados.observacoes()));
        ficha.setDataAtualizacao(clock.instant());

        String obs = cripto.decifrar(ficha.getObservacoes());
        return FichaSaudeResponse.de(ficha, obs);
    }

    // ---- EXCLUIR ----
    public void excluir(UUID alunoId, UUID fichaId, UsuarioAtual usuario) {
        exigirGestao(usuario);
        FichaSaude ficha = buscar(alunoId, fichaId);
        repository.delete(ficha);
    }

    // ============ auxiliares ============

    private FichaSaude buscar(UUID alunoId, UUID fichaId) {
        FichaSaude ficha =
                repository.findById(fichaId).orElseThrow(FichaSaudeNaoEncontradaException::new);
        if (!ficha.getAlunoId().equals(alunoId)) {
            throw new FichaSaudeNaoEncontradaException();
        }
        return ficha;
    }

    private void exigirGestao(UsuarioAtual usuario) {
        if (usuario.perfil() != Perfil.DIRECAO) {
            throw new AcessoNegadoException("Apenas a direção pode gerenciar fichas de saúde.");
        }
    }

    private void exigirAcessoLeitura(UsuarioAtual usuario, UUID alunoId) {
        if (usuario == null || usuario.perfil() == null) {
            throw new AcessoNegadoException("Usuário não autenticado.");
        }
    }

    private void validarVersao(FichaSaude ficha, Long versao) {
        if (versao == null || !Objects.equals(ficha.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }
}