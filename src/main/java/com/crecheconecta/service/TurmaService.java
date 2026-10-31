package com.crecheconecta.service;

import com.crecheconecta.dto.AtualizarTurmaRequest;
import com.crecheconecta.dto.CadastrarTurmaRequest;
import com.crecheconecta.dto.TurmaResponse;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Turma;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.exception.TurmaNaoEncontradaException;
import com.crecheconecta.repository.TurmaRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.crecheconecta.repository.MatriculaTurmaRepository;
import java.time.Clock;

import java.util.Objects;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class TurmaService {

    private final TurmaRepository turmas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final MatriculaTurmaRepository matriculas;
    private final Clock clock;

    public TurmaService(TurmaRepository turmas, UsuarioRepository usuarios, AuditoriaService auditoria, MatriculaTurmaRepository matriculas, Clock clock) {

        this.turmas = turmas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.matriculas = matriculas;
        this.clock = clock;
    }

    @Transactional
    public TurmaResponse cadastrar(CadastrarTurmaRequest request, UsuarioAtual solicitante) {

        validarDirecao(solicitante);


        var turma = new Turma();
        turma.setNome(normalizarTexto(request.nome(), "nome", 150));
        turma.setNumeroSala(normalizarTexto(request.numeroSala(), "número da sala", 20));
        turma.setAtivo(true);

        turmas.saveAndFlush(turma);

        auditoria.aposCommit("TURMA_CADASTRADA", solicitante.id(), turma.getId());

        return TurmaResponse.de(turma);
    }

    @Transactional(readOnly = true)
    public Page<TurmaResponse> listar(int pagina, int tamanho, Boolean ativo, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id").ascending()));

        Page<Turma> resultado;

        if (ativo == null) {
            resultado = turmas.findAll(paginacao);
        } else {
            resultado = turmas.findByAtivo(ativo, paginacao);
        }

        return resultado.map(TurmaResponse::de);
    }

    @Transactional(readOnly = true)
    public TurmaResponse visualizar(UUID turmaId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var turma = turmas.findById(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        return TurmaResponse.de(turma);
    }

    @Transactional
    public TurmaResponse atualizar(UUID turmaId, AtualizarTurmaRequest request, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var turma = buscarComBloqueio(turmaId, request.versao());

        turma.setNome(normalizarTexto(request.nome(), "nome", 150));
        turma.setNumeroSala(normalizarTexto(request.numeroSala(), "número da sala", 20));

        turmas.flush();

        auditoria.aposCommit("TURMA_ATUALIZADA", solicitante.id(), turma.getId());

        return TurmaResponse.de(turma);
    }

    @Transactional
    public TurmaResponse inativar(UUID turmaId, Long versao, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var turma = buscarComBloqueio(turmaId, versao);

        if (!turma.isAtivo()) {

            throw new RegraNegocioException("A turma já está inativa.");
        }

        turma.setAtivo(false);

        matriculas.encerrarDaTurma(turmaId, clock.instant());

        turmas.flush();

        auditoria.aposCommit("TURMA_INATIVADA", solicitante.id(), turma.getId());

        return TurmaResponse.de(turma);
    }

    private Turma buscarComBloqueio(UUID turmaId, Long versao) {

        var turma = turmas.buscarPorIdComBloqueio(turmaId).orElseThrow(TurmaNaoEncontradaException::new);

        if (versao == null || !Objects.equals(turma.getVersao(), versao)) {

            throw new ConflitoVersaoException();
        }

        return turma;
    }

    private String normalizarTexto(String valor, String campo, int limite) {

        if (valor == null || valor.isBlank()) {

            throw new RegraNegocioException("Informe o campo " + campo + ".");
        }

        String normalizado = valor.strip();

        if (normalizado.length() > limite) {

            throw new RegraNegocioException("O campo " + campo + " deve possuir no máximo " + limite + " caracteres.");
        }

        return normalizado;
    }

    private void validarDirecao(UsuarioAtual solicitante) {

        if (solicitante == null || solicitante.perfil() != Perfil.DIRECAO) {
            throw acessoNegado();
        }

        var diretor = usuarios.findById(solicitante.id()).orElseThrow(this::acessoNegado);

        if (!diretor.isAtivo() || diretor.getPerfil() != Perfil.DIRECAO) {

            throw acessoNegado();
        }
    }

    private AccessDeniedException acessoNegado() {

        return new AccessDeniedException("Acesso restrito à direção.");
    }
}