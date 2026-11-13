package com.crecheconecta.service;

import com.crecheconecta.entity.EventoAuditoria;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.exception.AcessoNegadoException;
import com.crecheconecta.exception.RegraNegocioException;
import com.crecheconecta.repository.AuditoriaRepository;
import com.crecheconecta.security.UsuarioAtual;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    // A mesma regra protege a busca, a leitura e o envio.
    private static final String CONTATOS_PERMITIDOS = """
        SELECT u.id, u.nome, u.perfil
        FROM usuarios eu
        JOIN usuarios u ON u.id <> eu.id AND u.perfil <> eu.perfil
        WHERE eu.id = :eu AND eu.ativo AND eu.situacao_cadastro = 'APROVADO'
          AND u.ativo AND u.situacao_cadastro = 'APROVADO'
          AND (
              eu.perfil = 'DIRECAO' OR u.perfil = 'DIRECAO'
              OR (
                  eu.perfil IN ('PROFESSOR', 'RESPONSAVEL')
                  AND u.perfil IN ('PROFESSOR', 'RESPONSAVEL')
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

    public ChatService(NamedParameterJdbcTemplate jdbc, TermoService termos, CriptografiaService criptografia, AuditoriaRepository auditoria) {
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
            SELECT p.id, p.nome, p.perfil, c.ultima_mensagem_em
            FROM permitidos p
            LEFT JOIN chat_conversas c
              ON c.participante_a = LEAST(:eu, p.id) AND c.participante_b = GREATEST(:eu, p.id)
            WHERE (NOT :somente OR c.id IS NOT NULL)
              AND strpos(lower(p.nome), lower(:busca)) > 0
            ORDER BY c.ultima_mensagem_em DESC NULLS LAST, lower(p.nome), p.id
            LIMIT :limite OFFSET :offset
            """, parametros, (rs, i) -> {
            var ultima = rs.getObject("ultima_mensagem_em", OffsetDateTime.class);
            return new Contato(rs.getObject("id", UUID.class), rs.getString("nome"),
                    Perfil.valueOf(rs.getString("perfil")), ultima == null ? null : ultima.toInstant());
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
            SELECT m.ordem, m.id, m.autor_id, m.texto_cifrado, m.criada_em
            FROM chat_mensagens m JOIN chat_conversas c ON c.id = m.conversa_id
            WHERE c.participante_a = LEAST(:eu, :contato)
              AND c.participante_b = GREATEST(:eu, :contato)
              AND m.ordem < :antes
            ORDER BY m.ordem DESC
            LIMIT :limite
            """, parametros, (rs, i) -> new Linha(rs.getLong("ordem"), new Mensagem(
                rs.getObject("id", UUID.class), rs.getObject("autor_id", UUID.class),
                criptografia.decifrar(rs.getString("texto_cifrado")),
                rs.getObject("criada_em", OffsetDateTime.class).toInstant())));

        var pagina = primeiros(registros);
        var cursor = registros.size() > TAMANHO ? Long.toString(pagina.getLast().ordem()) : null;
        return new PaginaMensagens(pagina.reversed().stream().map(Linha::mensagem).toList(), cursor);
    }

    @Transactional
    public Mensagem enviar(UsuarioAtual eu, UUID contato, String texto) {
        validarAceite(eu);
        if (texto == null || texto.isBlank() || texto.length() > 2000 || texto.indexOf(0) >= 0) {
            throw new RegraNegocioException("Escreva uma mensagem de até 2.000 caracteres.");
        }

        validarContato(eu.id(), contato);
        var parametros = par(eu.id(), contato);
        parametros.addValue("novo", UUID.randomUUID());

        int criada = jdbc.update("""
            INSERT INTO chat_conversas (id, participante_a, participante_b)
            VALUES (:novo, LEAST(:eu, :contato), GREATEST(:eu, :contato))
            ON CONFLICT (participante_a, participante_b) DO NOTHING
            """, parametros);

        var conversa = jdbc.queryForObject("""
            SELECT id FROM chat_conversas
            WHERE participante_a = LEAST(:eu, :contato) AND participante_b = GREATEST(:eu, :contato)
            FOR UPDATE
            """, parametros, UUID.class);

        // O vínculo pode mudar enquanto o envio aguarda o bloqueio da conversa.
        validarAceite(eu);
        validarContato(eu.id(), contato);

        var id = UUID.randomUUID();
        var corpo = texto.strip();
        var gravacao = new MapSqlParameterSource("id", id)
                .addValue("conversa", conversa)
                .addValue("autor", eu.id())
                .addValue("texto", criptografia.cifrar(corpo));

        var data = jdbc.queryForObject("""
            INSERT INTO chat_mensagens (id, conversa_id, autor_id, texto_cifrado, criada_em)
            VALUES (:id, :conversa, :autor, :texto, clock_timestamp())
            RETURNING criada_em
            """, gravacao, (rs, i) -> rs.getObject(1, OffsetDateTime.class));

        jdbc.update("UPDATE chat_conversas SET ultima_mensagem_em = :data WHERE id = :id",
                Map.of("data", data, "id", conversa));

        if (criada == 1) {
            registrar("CHAT_CONVERSA_CRIADA", eu.id(), conversa);
        }
        registrar("CHAT_MENSAGEM_ENVIADA", eu.id(), id);
        return new Mensagem(id, eu.id(), corpo, data.toInstant());
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
            throw new AcessoNegadoException("Este contato não está disponível para conversa.");
        }
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
    public record Mensagem(UUID id, UUID autorId, String texto, Instant criadaEm) {}
    public record PaginaContatos(List<Contato> itens, boolean temProxima) {}
    public record PaginaMensagens(List<Mensagem> itens, String cursorAnterior) {}
    private record Linha(long ordem, Mensagem mensagem) {}
}