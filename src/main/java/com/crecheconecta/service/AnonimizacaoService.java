package com.crecheconecta.service;

import com.crecheconecta.dto.AnonimizacaoResponse;
import com.crecheconecta.entity.Aluno;
import com.crecheconecta.entity.AlunoResponsavel;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.SituacaoCadastro;
import com.crecheconecta.entity.Usuario;
import com.crecheconecta.exception.AlunoNaoEncontradoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AlunoRepository;
import com.crecheconecta.repository.AlunoResponsavelRepository;
import com.crecheconecta.repository.ConviteCadastroRepository;
import com.crecheconecta.repository.DiarioRotinaRepository;
import com.crecheconecta.repository.InformacaoRelevanteRepository;
import com.crecheconecta.repository.MarcacaoItemMalaRepository;
import com.crecheconecta.repository.ObservacaoRepository;
import com.crecheconecta.repository.SessaoRepository;
import com.crecheconecta.repository.UsuarioRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LGPD: anonimização dos dados quando a criança sai da creche (aluno já inativo).
 * Feita pela direção, numa única transação: ou tudo é anonimizado, ou nada muda.
 *
 * Apagado: informações relevantes (dado de saúde), observações dos professores, mensagens do chat
 * e notificações do responsável, textos livres do diário e da mala.
 * Substituído: nome e data de nascimento do aluno (fica só o ano) e nome, e-mail, telefone e senha
 * do responsável (a conta é inativada e as sessões revogadas). Os convites com o e-mail dele também.
 * Mantido sem identificação: frequência, matrícula, opções do diário, aceites dos termos (prova do
 * consentimento) e auditoria (só guarda IDs).
 */
@Service
public class AnonimizacaoService {

    static final String NOME_ALUNO = "Aluno anonimizado";
    static final String NOME_RESPONSAVEL = "Responsável anonimizado";

    private final AlunoRepository alunos;
    private final AlunoResponsavelRepository vinculos;
    private final UsuarioRepository usuarios;
    private final InformacaoRelevanteRepository informacoes;
    private final ObservacaoRepository observacoes;
    private final DiarioRotinaRepository diarios;
    private final MarcacaoItemMalaRepository marcacoes;
    private final ConviteCadastroRepository convites;
    private final SessaoRepository sessoes;
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AnonimizacaoService(AlunoRepository alunos, AlunoResponsavelRepository vinculos, UsuarioRepository usuarios, InformacaoRelevanteRepository informacoes, ObservacaoRepository observacoes, DiarioRotinaRepository diarios, MarcacaoItemMalaRepository marcacoes, ConviteCadastroRepository convites, SessaoRepository sessoes, NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, AuditoriaService auditoria, Clock clock) {
        this.alunos = alunos;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.informacoes = informacoes;
        this.observacoes = observacoes;
        this.diarios = diarios;
        this.marcacoes = marcacoes;
        this.convites = convites;
        this.sessoes = sessoes;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public AnonimizacaoResponse anonimizarAluno(UUID alunoId, UsuarioAtual solicitante) {
        exigirDirecaoAtiva(solicitante);

        Aluno aluno = alunos.findById(alunoId).orElseThrow(AlunoNaoEncontradoException::new);
        if (aluno.getAnonimizadoEm() != null) {
            throw new RegraNegocioException("Os dados deste aluno já foram anonimizados.");
        }
        if (aluno.isAtivo()) {
            throw new RegraNegocioException("Inative o aluno antes de anonimizar os dados.");
        }

        Instant agora = clock.instant();

        // 1. Dados sobre a criança: o que é texto livre ou de saúde sai; o resto fica sem identificação.
        var informacoesDoAluno = informacoes.findByAlunoId(alunoId);
        informacoes.deleteAll(informacoesDoAluno);
        var observacoesDoAluno = observacoes.findByAlunoIdOrderByDataCriacaoDesc(alunoId);
        observacoes.deleteAll(observacoesDoAluno);
        diarios.apagarObservacoesDoAluno(alunoId);
        marcacoes.apagarObservacoesDoAluno(alunoId);

        // 2. Vínculos: encerra os atuais e junta todos os responsáveis que o aluno já teve.
        Set<UUID> responsaveis = new LinkedHashSet<>();
        for (AlunoResponsavel vinculo : vinculos.findByAlunoId(alunoId)) {
            responsaveis.add(vinculo.getResponsavelId());
            if (vinculo.getEncerradoEm() == null) {
                vinculo.setEncerradoEm(agora);
            }
        }
        vinculos.flush();

        // 3. Responsáveis: só quem não tem mais nenhuma criança vinculada na creche.
        int responsaveisAnonimizados = 0;
        for (UUID responsavelId : responsaveis) {
            if (!vinculos.existsByResponsavelIdAndEncerradoEmIsNull(responsavelId) && anonimizarResponsavel(responsavelId, agora)) {
                responsaveisAnonimizados++;
            }
        }

        // 4. Aluno: nome genérico e só o ano de nascimento (a V29 permite data nula com o ano).
        Integer ano = aluno.getAnoNascimento();
        aluno.setNome(NOME_ALUNO);
        aluno.setDataNascimento(null);
        aluno.setAnoNascimento(ano);
        aluno.setAnonimizadoEm(agora);
        alunos.saveAndFlush(aluno);

        auditoria.aposCommit("ALUNO_ANONIMIZADO", solicitante.id(), alunoId);
        return new AnonimizacaoResponse(alunoId, responsaveisAnonimizados, informacoesDoAluno.size(), observacoesDoAluno.size());
    }

    private boolean anonimizarResponsavel(UUID responsavelId, Instant agora) {
        Usuario responsavel = usuarios.findById(responsavelId).orElse(null);
        if (responsavel == null || responsavel.getPerfil() != Perfil.RESPONSAVEL || responsavel.getAnonimizadoEm() != null) {
            return false;
        }

        String emailAnterior = responsavel.getEmail();
        String emailAnonimo = "anonimizado-" + responsavel.getId() + "@anonimizado.invalid";

        // O e-mail é único no banco: um valor fictício por usuário, que não recebe e-mail.
        responsavel.setNome(NOME_RESPONSAVEL);
        responsavel.setEmail(emailAnonimo);
        responsavel.setTelefone(null);
        responsavel.setSenhaHash(encoder.encode(UUID.randomUUID().toString()));
        responsavel.setAtivo(false);
        responsavel.setSituacaoCadastro(SituacaoCadastro.INATIVO);
        responsavel.setTentativas(0);
        responsavel.setBloqueadoAte(null);
        responsavel.setAnonimizadoEm(agora);
        usuarios.saveAndFlush(responsavel);

        sessoes.revogarTodasDoUsuario(responsavelId, agora);

        for (var convite : convites.findByEmail(emailAnterior)) {
            convite.setNome(NOME_RESPONSAVEL);
            convite.setEmail(emailAnonimo);
        }
        convites.flush();

        apagarConversasENotificacoes(responsavelId);

        auditoria.aposCommit("RESPONSAVEL_ANONIMIZADO", null, responsavelId);
        return true;
    }

    /**
     * Chat e notificações são gravados por JDBC (sem entidade). No banco dos testes (H2, sem Flyway)
     * essas tabelas não existem, por isso a verificação antes de apagar.
     */
    private void apagarConversasENotificacoes(UUID usuarioId) {
        var parametros = new MapSqlParameterSource("usuario", usuarioId);

        if (tabelaExiste("chat_mensagens") && tabelaExiste("chat_conversas")) {
            jdbc.update("""
                    DELETE FROM chat_mensagens
                    WHERE conversa_id IN (SELECT id FROM chat_conversas WHERE participante_a = :usuario OR participante_b = :usuario)
                    """, parametros);
            jdbc.update("DELETE FROM chat_conversas WHERE participante_a = :usuario OR participante_b = :usuario", parametros);
        }
        if (tabelaExiste("notificacoes_comunicado")) {
            jdbc.update("DELETE FROM notificacoes_comunicado WHERE usuario_id = :usuario", parametros);
        }
    }

    private boolean tabelaExiste(String tabela) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = :tabela",
                new MapSqlParameterSource("tabela", tabela),
                Integer.class);
        return total != null && total > 0;
    }

    private void exigirDirecaoAtiva(UsuarioAtual solicitante) {
        if (solicitante == null || solicitante.id() == null || solicitante.perfil() != Perfil.DIRECAO) {
            throw acessoNegado();
        }
        usuarios.findById(solicitante.id())
                .filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == Perfil.DIRECAO && usuario.getSituacaoCadastro() == SituacaoCadastro.APROVADO)
                .orElseThrow(this::acessoNegado);
    }

    private AccessDeniedException acessoNegado() {
        return new AccessDeniedException("A anonimização de dados é restrita à direção.");
    }
}
