CREATE TABLE chat_conversas (
                                id UUID PRIMARY KEY,
                                participante_a UUID NOT NULL REFERENCES usuarios(id),
                                participante_b UUID NOT NULL REFERENCES usuarios(id),
                                ultima_mensagem_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                UNIQUE (participante_a, participante_b),
                                CHECK (participante_a < participante_b)
);

CREATE INDEX idx_chat_conversa_participante_b
    ON chat_conversas (participante_b);

CREATE TABLE chat_mensagens (
                                ordem BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                id UUID NOT NULL UNIQUE,
                                conversa_id UUID NOT NULL REFERENCES chat_conversas(id),
                                autor_id UUID NOT NULL REFERENCES usuarios(id),
                                texto_cifrado TEXT NOT NULL,
                                criada_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_chat_mensagens_historico
    ON chat_mensagens (conversa_id, ordem DESC);

CREATE TABLE notificacoes_comunicado (
                                         usuario_id UUID NOT NULL REFERENCES usuarios(id),
                                         comunicado_id UUID NOT NULL REFERENCES comunicados(id),
                                         criada_em TIMESTAMPTZ NOT NULL,
                                         lida_em TIMESTAMPTZ,
                                         PRIMARY KEY (usuario_id, comunicado_id)
);

CREATE INDEX idx_notificacoes_comunicado_lista
    ON notificacoes_comunicado (usuario_id, criada_em DESC, comunicado_id DESC);

CREATE INDEX idx_notificacoes_comunicado_pendentes
    ON notificacoes_comunicado (usuario_id, comunicado_id)
    WHERE lida_em IS NULL;

-- Cria os avisos na mesma transação que publica o comunicado.
CREATE FUNCTION notificar_novo_comunicado() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.ativo THEN
        INSERT INTO notificacoes_comunicado (usuario_id, comunicado_id, criada_em)
SELECT id, NEW.id, NEW.criado_em
FROM usuarios
WHERE ativo AND situacao_cadastro = 'APROVADO'
  AND perfil IN ('DIRECAO', 'PROFESSOR', 'RESPONSAVEL');
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notificar_novo_comunicado
    AFTER INSERT ON comunicados
    FOR EACH ROW EXECUTE FUNCTION notificar_novo_comunicado();