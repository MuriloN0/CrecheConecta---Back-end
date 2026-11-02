CREATE TABLE alunos (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    data_nascimento DATE NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_alunos_nome_nao_vazio CHECK (LENGTH(TRIM(nome)) > 0)
);

CREATE INDEX idx_alunos_nome_id ON alunos (nome, id);