package com.crecheconecta.service;

import com.crecheconecta.entity.EventoAuditoria;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AuditoriaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatService {

    private static final int TAMANHO = 30;

    private static final String MENSAGEM_EXPIRADA = """
        NOT m.favorita_participante_a
        AND NOT m.favorita_participante_b
        AND m.criada_em <= statement_timestamp() - INTERVAL '168 hours'
        """;

    private static final String CONTATOS_PERMITIDOS = """
        SELECT u.id, u.nome, u.perfil, c.id AS conversa_id, c.ultima_mensagem_em,
               (eu.perfil = 'RESPONSAVEL' AND u.perfil = 'DIRECAO') AS somente_em_conversa
        FROM usuarios eu
        JOIN usuarios u ON u.id <> eu.id
        LEFT JOIN chat_conversas c
          ON c.participante_a = LEAST(eu.id, u.id)
         AND c.participante_b = GREATEST(eu.id, u.id)
        WHERE eu.id = :eu AND eu.ativo AND eu.situacao_cadastro = 'APROVADO'
          AND u.ativo AND u.situacao_cadastro = 'APROVADO'
          AND (
              (eu.perfil = 'DIRECAO' AND u.perfil IN ('DIRECAO', 'PROFESSOR', 'RESPONSAVEL'))
              OR (eu.perfil = 'PROFESSOR' AND u.perfil IN ('DIRECAO', 'PROFESSOR'))
              OR (eu.perfil = 'RESPONSAVEL' AND u.perfil = 'DIRECAO' AND c.id IS NOT NULL)
              OR (
                  (
                      (eu.perfil = 'PROFESSOR' AND u.perfil = 'RESPONSAVEL')
                      OR (eu.perfil = 'RESPONSAVEL' AND u.perfil = 'PROFESSOR')
                  )
                  AND EXISTS (
                      SELECT 1 FROM alunos_responsaveis ar
                      JOIN alunos a ON a.id = ar.aluno_id AND a.ativo
                      JOIN matriculas_turmas m ON m.aluno_id = a.id AND m.encerrado_em IS NULL
                      JOIN turmas t ON t.id = m.turma_id AND t.ativo
                      JOIN turmas_professores tp ON tp.turma_id = t.id AND tp.encerrado_em IS NULL
                      WHERE ar.encerrado_em IS NULL
                        AND ar.responsavel_id = CASE WHEN eu.perfil = 'RESPONSAVEL' THEN eu.id ELSE u.id END
                        AND tp.professor_id = CASE WHEN eu.perfil = 'PROFESSOR' THEN eu.id ELSE u.id END
                  )
              )
          )
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TermoService termos;
    private final CriptografiaService criptografia;
    private final AuditoriaRepository auditoria;

    public ChatService(
            NamedParameterJdbcTemplate jdbc,
            TermoService termos,
            CriptografiaService criptografia,
            AuditoriaRepository auditoria
    ) {
        this.jdbc = jdbc;
        this.termos = termos;
        this.criptografia = criptografia;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PaginaContatos contatos(UsuarioAtual eu, String busca, int pagina, boolean somenteConversas) {
        validarAceite(eu);

        if (pagina < 0 || busca.length() > 100) {
            throw new RegraNegocioException("Confira a página e o texto da busca.");
        }

        var parametros = new MapSqlParameterSource("eu", eu.id())
                .addValue("busca", busca.strip())
                .addValue("somente", somenteConversas)
                .addValue("limite", TAMANHO + 1)
                .addValue("offset", (long) pagina * TAMANHO);

        var registros = jdbc.query("""
            WITH permitidos AS (
            """ + CONTATOS_PERMITIDOS + """
            )
            SELECT id, nome, perfil, ultima_mensagem_em
            FROM permitidos
            WHERE ((:somente AND conversa_id IS NOT NULL) OR (NOT :somente AND NOT somente_em_conversa))
              AND strpos(lower(nome), lower(:busca)) > 0
            ORDER BY ultima_mensagem_em DESC NULLS LAST, lower(nome), id
            LIMIT :limite OFFSET :offset
            """, parametros, (rs, i) -> {
            var ultima = rs.getObject("ultima_mensagem_em", OffsetDateTime.class);
            return new Contato(
                    rs.getObject("id", UUID.class),
                    rs.getString("nome"),
                    Perfil.valueOf(rs.getString("perfil")),
                    ultima == null ? null : ultima.toInstant()
            );
        });

        return new PaginaContatos(primeiros(registros), registros.size() > TAMANHO);
    }

    @Transactional(readOnly = true)
    public PaginaMensagens mensagens(UsuarioAtual eu, UUID contato, Long antes) {
        validarAceite(eu);
        if (antes != null && antes <= 0) {
            throw new RegraNegocioException("Cursor de mensagens inválido.");
        }

        validarContato(eu.id(), contato);
        var parametros = par(eu.id(), contato)
                .addValue("antes", antes == null ? Long.MAX_VALUE : antes)
                .addValue("limite", TAMANHO + 1);

        var registros = jdbc.query("""
            SELECT m.ordem, m.id, m.autor_id, m.texto_cifrado, m.criada_em,
                   CASE WHEN c.participante_a = :eu THEN m.favorita_participante_a
                        ELSE m.favorita_participante_b END AS minha_favorita,
                   (m.favorita_participante_a OR m.favorita_participante_b) AS preservada
            FROM chat_mensagens m JOIN chat_conversas c ON c.id = m.conversa_id
            WHERE c.participante_a = LEAST(:eu, :contato)
              AND c.participante_b = GREATEST(:eu, :contato)
              AND m.ordem < :antes
              AND NOT (
            """ + MENSAGEM_EXPIRADA + """
              )
            ORDER BY m.ordem DESC
            LIMIT :limite
            """, parametros, (rs, i) -> new Mensagem(
                rs.getObject("id", UUID.class),
                rs.getObject("autor_id", UUID.class),
                criptografia.decifrar(rs.getString("texto_cifrado")),
                rs.getObject("criada_em", OffsetDateTime.class).toInstant(),
                Long.toString(rs.getLong("ordem")),
                rs.getBoolean("minha_favorita"),
                rs.getBoolean("preservada")
        ));

        var pagina = primeiros(registros);
        var cursor = registros.size() > TAMANHO ? pagina.getLast().ordem() : null;
        return new PaginaMensagens(List.copyOf(pagina.reversed()), cursor);
    }

    @Transactional
    public Mensagem enviar(UsuarioAtual eu, UUID contato, String texto) {
        validarAceite(eu);
        if (texto == null || texto.isBlank() || texto.length() > 4000 || texto.indexOf(0) >= 0) {
            throw new RegraNegocioException("Escreva uma mensagem de até 4.000 caracteres.");
        }

        validarContato(eu.id(), contato);
        var parametros = par(eu.id(), contato).addValue("novo", UUID.randomUUID());

        int criada = jdbc.update("""
            WITH permitidos AS (
            """ + CONTATOS_PERMITIDOS + """
            )
            INSERT INTO chat_conversas (id, participante_a, participante_b)
            SELECT :novo, LEAST(:eu, id), GREATEST(:eu, id)
            FROM permitidos
            WHERE id = :contato AND NOT somente_em_conversa
            ON CONFLICT (participante_a, participante_b) DO NOTHING
            """, parametros);

        var conversas = jdbc.queryForList("""
            SELECT id FROM chat_conversas
            WHERE participante_a = LEAST(:eu, :contato) AND participante_b = GREATEST(:eu, :contato)
            FOR UPDATE
            """, parametros, UUID.class);

        if (conversas.isEmpty()) {
            throw contatoIndisponivel();
        }

        var conversa = conversas.getFirst();
        validarAceite(eu);
        validarContato(eu.id(), contato);

        var id = UUID.randomUUID();
        var corpo = texto.strip();
        var gravacao = new MapSqlParameterSource("id", id)
                .addValue("conversa", conversa)
                .addValue("autor", eu.id())
                .addValue("texto", criptografia.cifrar(corpo));

        var mensagem = jdbc.queryForObject("""
            INSERT INTO chat_mensagens (id, conversa_id, autor_id, texto_cifrado, criada_em)
            VALUES (:id, :conversa, :autor, :texto, clock_timestamp())
            RETURNING criada_em, ordem
            """, gravacao, (rs, i) -> new Mensagem(
                id, eu.id(), corpo,
                rs.getObject("criada_em", OffsetDateTime.class).toInstant(),
                Long.toString(rs.getLong("ordem")),
                false, false
        ));

        jdbc.update("UPDATE chat_conversas SET ultima_mensagem_em = :data WHERE id = :id",
                Map.of("data", OffsetDateTime.ofInstant(mensagem.criadaEm(), java.time.ZoneOffset.UTC),
                        "id", conversa));

        if (criada == 1) {
            registrar("CHAT_CONVERSA_CRIADA", eu.id(), conversa);
        }
        registrar("CHAT_MENSAGEM_ENVIADA", eu.id(), id);
        return mensagem;
    }

    @Transactional
    public boolean alterarFavorito(UsuarioAtual eu, UUID contato, UUID mensagem, boolean favorita) {
        validarAceite(eu);
        validarContato(eu.id(), contato);

        var parametros = par(eu.id(), contato)
                .addValue("mensagem", mensagem)
                .addValue("favorita", favorita);

        return jdbc.update("""
            UPDATE chat_mensagens m
            SET favorita_participante_a = CASE WHEN c.participante_a = :eu THEN :favorita
                                              ELSE m.favorita_participante_a END,
                favorita_participante_b = CASE WHEN c.participante_b = :eu THEN :favorita
                                              ELSE m.favorita_participante_b END
            FROM chat_conversas c
            WHERE c.id = m.conversa_id
              AND c.participante_a = LEAST(:eu, :contato)
              AND c.participante_b = GREATEST(:eu, :contato)
              AND m.id = :mensagem
              AND NOT (
            """ + MENSAGEM_EXPIRADA + """
              )
            """, parametros) == 1;
    }

    @Scheduled(cron = "${app.chat.retencao-cron:0 */5 * * * *}")
    public void limparMensagensExpiradas() {
        jdbc.update("DELETE FROM chat_mensagens m WHERE " + MENSAGEM_EXPIRADA, Map.of());
    }

    private void validarAceite(UsuarioAtual eu) {
        if (eu.perfil() == Perfil.RESPONSAVEL && termos.precisaAceitar(eu.id())) {
            throw new AcessoNegadoException("Aceite os termos vigentes para acessar as conversas.");
        }
    }

    private void validarContato(UUID eu, UUID contato) {
        boolean permitido = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM (
            """ + CONTATOS_PERMITIDOS + """
            ) permitidos WHERE id = :contato)
            """, par(eu, contato), Boolean.class));

        if (!permitido) {
            throw contatoIndisponivel();
        }
    }

    private AcessoNegadoException contatoIndisponivel() {
        return new AcessoNegadoException("Este contato não está disponível para conversa.");
    }

    private void registrar(String acao, UUID usuario, UUID recurso) {
        var evento = new EventoAuditoria();
        evento.setAcao(acao);
        evento.setUsuarioId(usuario);
        evento.setRecursoId(recurso);
        auditoria.save(evento);
    }

    private static MapSqlParameterSource par(UUID eu, UUID contato) {
        return new MapSqlParameterSource("eu", eu).addValue("contato", contato);
    }

    private static <T> List<T> primeiros(List<T> registros) {
        return registros.subList(0, Math.min(registros.size(), TAMANHO));
    }

    public record Contato(UUID id, String nome, Perfil perfil, Instant ultimaMensagemEm) {}
    public record Mensagem(
            UUID id, UUID autorId, String texto, Instant criadaEm,
            String ordem, boolean minhaFavorita, boolean preservada
    ) {}
    public record PaginaContatos(List<Contato> itens, boolean temProxima) {}
    public record PaginaMensagens(List<Mensagem> itens, String cursorAnterior) {}
}