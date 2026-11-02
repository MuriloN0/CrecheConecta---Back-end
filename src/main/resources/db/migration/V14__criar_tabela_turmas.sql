CREATE TABLE turmas (
     id UUID PRIMARY KEY,
     nome VARCHAR(150) NOT NULL,
     numero_sala VARCHAR(20) NOT NULL,
     ativo BOOLEAN NOT NULL DEFAULT TRUE,
     versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_turmas_nome_nao_vazio
        CHECK (LENGTH(TRIM(nome)) > 0),

    CONSTRAINT ck_turmas_sala_nao_vazia
        CHECK (LENGTH(TRIM(numero_sala)) > 0)
);

CREATE INDEX idx_turmas_nome_id
    ON turmas (nome, id);