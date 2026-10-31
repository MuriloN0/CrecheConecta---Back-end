package com.crecheconecta.service;

import com.crecheconecta.dto.FichaSaudeResponse;
import com.crecheconecta.dto.FichaSaudeResumoResponse;
import com.crecheconecta.entity.FichaSaude;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.FichaSaudeNaoEncontradaException;
import com.crecheconecta.model.DadosFichaSaude;
import com.crecheconecta.repository.FichaSaudeRepository;
import com.crecheconecta.security.UsuarioAtual;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@Transactional
@PreAuthorize("hasRole('DIRECAO')")
public class FichaSaudeService {

    private final FichaSaudeRepository repository;
    private final CriptografiaService cripto;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;

    public FichaSaudeService(FichaSaudeRepository repository, CriptografiaService cripto, AuditoriaService auditoria, Clock clock, AlunoRepository alunos, UsuarioRepository usuarios) {

        this.repository = repository;
        this.cripto = cripto;
        this.auditoria = auditoria;
        this.clock = clock;
        this.alunos = alunos;
        this.usuarios = usuarios;
    }

    // ---- CADASTRAR ----
    public UUID cadastrar(UUID alunoId, DadosFichaSaude dados, UsuarioAtual usuario) {
        exigirGestao(usuario);
        exigirAlunoExistente(alunoId);

        FichaSaude ficha = new FichaSaude();
        ficha.setAlunoId(alunoId);
        ficha.setNome(dados.nome());
        ficha.setObservacoes(cripto.cifrar(dados.observacoes()));
        Instant agora = clock.instant();
        ficha.setDataCriacao(agora);
        ficha.setDataAtualizacao(agora);

        repository.save(ficha);
        auditoria.aposCommit("FICHA_CADASTRADA", usuario.id(), ficha.getId());
        return ficha.getId();
    }

    @Transactional(readOnly = true)
    public List<FichaSaudeResumoResponse> listar(UUID alunoId, UsuarioAtual usuario) {
        exigirAcessoLeitura(usuario, alunoId);
        List<FichaSaudeResumoResponse> lista =
                repository.findByAlunoId(alunoId).stream()
                        .map(FichaSaudeResumoResponse::de)
                        .toList();
        auditoria.registrar("FICHAS_LISTADAS", usuario.id(), alunoId);
        return lista;
    }

    @Transactional(readOnly = true)
    public FichaSaudeResponse visualizar(UUID alunoId, UUID fichaId, UsuarioAtual usuario) {
        exigirAcessoLeitura(usuario, alunoId);
        FichaSaude ficha = buscar(alunoId, fichaId);
        String obs = cripto.decifrar(ficha.getObservacoes());
        auditoria.registrar("FICHA_CONSULTADA", usuario.id(), fichaId);
        return FichaSaudeResponse.de(ficha, obs);
    }

    public FichaSaudeResponse atualizar(UUID alunoId, UUID fichaId, DadosFichaSaude dados, Long versao, UsuarioAtual usuario) {

        exigirGestao(usuario);
        exigirAlunoExistente(alunoId);

        FichaSaude ficha = buscar(alunoId, fichaId);
        validarVersao(ficha, versao);

        ficha.setNome(dados.nome());
        ficha.setObservacoes(cripto.cifrar(dados.observacoes()));
        ficha.setDataAtualizacao(clock.instant());
        repository.flush();

        auditoria.aposCommit("FICHA_EDITADA", usuario.id(), ficha.getId());
        String obs = cripto.decifrar(ficha.getObservacoes());
        return FichaSaudeResponse.de(ficha, obs);
    }

    public void excluir(UUID alunoId, UUID fichaId, UsuarioAtual usuario) {

        exigirGestao(usuario);
        exigirAlunoExistente(alunoId);
        FichaSaude ficha = buscar(alunoId, fichaId);
        repository.delete(ficha);
        auditoria.aposCommit("FICHA_EXCLUIDA", usuario.id(), fichaId);
    }



    private FichaSaude buscar(UUID alunoId, UUID fichaId) {

        FichaSaude ficha = repository.findById(fichaId).orElseThrow(FichaSaudeNaoEncontradaException::new);

        if (!ficha.getAlunoId().equals(alunoId)) {
            throw new FichaSaudeNaoEncontradaException();
        }

        return ficha;
    }

    private void exigirGestao(UsuarioAtual usuario) {
        if (usuario == null || usuario.perfil() != Perfil.DIRECAO) {

            throw new AcessoNegadoException("Apenas a direção pode gerenciar fichas de saúde.");
        }

        var diretor = usuarios.findById(usuario.id()).orElseThrow(() -> new AcessoNegadoException("Acesso restrito à direção."));

        if (!diretor.isAtivo() || diretor.getPerfil() != Perfil.DIRECAO) {

            throw new AcessoNegadoException("Acesso restrito à direção.");
        }
    }

    private void exigirAcessoLeitura(UsuarioAtual usuario, UUID alunoId) {

        exigirGestao(usuario);
        exigirAlunoExistente(alunoId);
    }

    private void exigirAlunoExistente(UUID alunoId) {
        if (!alunos.existsById(alunoId)) {

            throw new AlunoNaoEncontradoException();
        }
    }

    private void validarVersao(FichaSaude ficha, Long versao) {
        if (versao == null || !Objects.equals(ficha.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }
    }
}