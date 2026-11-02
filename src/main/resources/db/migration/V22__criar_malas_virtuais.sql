-- Mala Virtual: lista do que o aluno precisa levar para a creche (fralda, roupa, pomada...).
-- O PROFESSOR monta a mala (feature futura); o RESPONSAVEL visualiza o que precisa enviar.
-- Regra: 1 mala por aluno por dia; cada mala tem vários itens.
CREATE TABLE malas_virtuais (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    turma_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    data DATE NOT NULL,
    observacao VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_malas_aluno
        FOREIGN KEY (aluno_id)
            REFERENCES alunos (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_malas_turma
        FOREIGN KEY (turma_id)
            REFERENCES turmas (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_malas_professor
        FOREIGN KEY (professor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_malas_aluno_data
        UNIQUE (aluno_id, data)
);

CREATE TABLE itens_mala (
    id UUID PRIMARY KEY,
    mala_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    quantidade INTEGER NOT NULL,
    enviado BOOLEAN NOT NULL DEFAULT FALSE,

    -- Item não existe sem a mala: apagar a mala apaga os itens.
    CONSTRAINT fk_itens_mala
        FOREIGN KEY (mala_id)
            REFERENCES malas_virtuais (id)
            ON DELETE CASCADE,

    CONSTRAINT ck_itens_mala_quantidade
        CHECK (quantidade > 0)
);

CREATE INDEX idx_itens_mala_mala
    ON itens_mala (mala_id);
