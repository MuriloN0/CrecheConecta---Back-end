package com.crecheconecta.service;

import com.crecheconecta.dto.AlunoResponse;
import com.crecheconecta.dto.AtualizarAlunoRequest;
import com.crecheconecta.dto.CadastrarAlunoRequest;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.ConflitoVersaoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('DIRECAO')")
public class AlunoService {

    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final MatriculaTurmaRepository matriculas;

    public AlunoService(AlunoRepository alunos, UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock,  MatriculaTurmaRepository matriculas) {

        this.alunos = alunos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
        this.matriculas = matriculas;
    }

    @Transactional
    public AlunoResponse cadastrar(CadastrarAlunoRequest request, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        String nome = validarDados(request.nome(), request.dataNascimento());

        var aluno = new Aluno();
        aluno.setNome(nome);
        aluno.setDataNascimento(request.dataNascimento());
        aluno.setAtivo(true);

        alunos.saveAndFlush(aluno);

        auditoria.aposCommit("ALUNO_CADASTRADO", solicitante.id(), aluno.getId());

        return AlunoResponse.de(aluno);
    }

    @Transactional(readOnly = true)
    public Page<AlunoResponse> listar(int pagina, int tamanho, Boolean ativo, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        if (pagina < 0 || tamanho < 1 || tamanho > 100) {

            throw new RegraNegocioException("Informe página a partir de zero " + "e tamanho entre 1 e 100.");
        }

        var paginacao = PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id").ascending()));

        Page<Aluno> resultado;

        if (ativo == null) {

            resultado = alunos.findAll(paginacao);
        } else {

            resultado = alunos.findByAtivo(ativo, paginacao);
        }

        return resultado.map(AlunoResponse::de);
    }

    @Transactional(readOnly = true)
    public AlunoResponse visualizar(UUID alunoId, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var aluno = alunos.findById(alunoId).orElseThrow(AlunoNaoEncontradoException::new);

        return AlunoResponse.de(aluno);
    }

    @Transactional
    public AlunoResponse atualizar(UUID alunoId, AtualizarAlunoRequest request, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var aluno = buscarComBloqueio(alunoId, request.versao());

        String nome = validarDados(request.nome(), request.dataNascimento());

        aluno.setNome(nome);
        aluno.setDataNascimento(request.dataNascimento());

        alunos.flush();

        auditoria.aposCommit("ALUNO_ATUALIZADO", solicitante.id(), aluno.getId());

        return AlunoResponse.de(aluno);
    }

    @Transactional
    public AlunoResponse inativar(UUID alunoId, Long versao, UsuarioAtual solicitante) {

        validarDirecao(solicitante);

        var aluno = buscarComBloqueio(alunoId, versao);

        if (!aluno.isAtivo()) {

            throw new RegraNegocioException("O aluno já está inativo.");
        }

        aluno.setAtivo(false);

        matriculas.encerrarDoAluno(alunoId, clock.instant());

        alunos.flush();

        auditoria.aposCommit("ALUNO_INATIVADO", solicitante.id(), aluno.getId());

        return AlunoResponse.de(aluno);
    }

    private Aluno buscarComBloqueio(UUID alunoId, Long versao) {

        var aluno = alunos.buscarPorIdComBloqueio(alunoId).orElseThrow(AlunoNaoEncontradoException::new);

        if (versao == null || !Objects.equals(aluno.getVersao(), versao)) {
            throw new ConflitoVersaoException();
        }

        return aluno;
    }

    private String validarDados(String nome, LocalDate dataNascimento) {

        if (nome == null || nome.isBlank()) {

            throw new RegraNegocioException("Informe o nome do aluno.");
        }

        String nomeNormalizado = nome.strip();

        if (nomeNormalizado.length() > 150) {

            throw new RegraNegocioException("O nome deve possuir no máximo 150 caracteres.");
        }

        if (dataNascimento == null || dataNascimento.isAfter(LocalDate.now(clock))) {

            throw new RegraNegocioException("Informe uma data de nascimento válida, " + "que não esteja no futuro.");
        }

        return nomeNormalizado;
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