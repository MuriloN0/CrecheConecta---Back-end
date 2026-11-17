ALTER TABLE chat_mensagens
    ADD COLUMN favorita_participante_a BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN favorita_participante_b BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_chat_mensagens_retencao
    ON chat_mensagens (criada_em)
    WHERE NOT favorita_participante_a AND NOT favorita_participante_b;