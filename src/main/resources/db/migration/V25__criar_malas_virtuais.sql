DROP TABLE IF EXISTS itens_mala;
DROP TABLE IF EXISTS malas_virtuais;

CREATE TABLE malas_virtuais (
    id UUID PRIMARY KEY,
    turma_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    data DATE NOT NULL,
    observacao VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_malas_turma
        FOREIGN KEY (turma_id)
            REFERENCES turmas (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_malas_professor
        FOREIGN KEY (professor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_malas_titulo
        CHECK (LENGTH(TRIM(titulo)) > 0)
);

CREATE INDEX idx_malas_virtuais_turma_data
    ON malas_virtuais (turma_id, data DESC);

CREATE TABLE itens_mala (
    id UUID PRIMARY KEY,
    mala_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    quantidade INTEGER NOT NULL,

    CONSTRAINT fk_itens_mala
        FOREIGN KEY (mala_id)
            REFERENCES malas_virtuais (id)
            ON DELETE CASCADE,

    CONSTRAINT ck_itens_mala_nome
        CHECK (LENGTH(TRIM(nome)) > 0),

    CONSTRAINT ck_itens_mala_quantidade
        CHECK (quantidade > 0)
);

CREATE INDEX idx_itens_mala_mala
    ON itens_mala (mala_id);

CREATE TABLE marcacoes_itens_mala (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL,
    aluno_id UUID NOT NULL,
    enviado BOOLEAN NOT NULL,
    observacao VARCHAR(500),
    marcado_por UUID NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_marcacoes_item
        FOREIGN KEY (item_id)
            REFERENCES itens_mala (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_marcacoes_aluno
        FOREIGN KEY (aluno_id)
            REFERENCES alunos (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_marcacoes_marcado_por
        FOREIGN KEY (marcado_por)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_marcacoes_item_aluno
        UNIQUE (item_id, aluno_id)
);

CREATE INDEX idx_marcacoes_itens_mala_aluno
    ON marcacoes_itens_mala (aluno_id);
