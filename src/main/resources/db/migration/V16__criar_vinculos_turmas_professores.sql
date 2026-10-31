CREATE TABLE turmas_professores (
    id UUID PRIMARY KEY,
    turma_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    encerrado_em TIMESTAMPTZ,

    CONSTRAINT fk_turmas_professores_turma
        FOREIGN KEY (turma_id)
            REFERENCES turmas (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_turmas_professores_usuario
        FOREIGN KEY (professor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_turmas_professores_datas
        CHECK (
            encerrado_em IS NULL
            OR encerrado_em >= criado_em
        )
);

CREATE UNIQUE INDEX uk_turma_professor_atual
    ON turmas_professores (turma_id, professor_id)
    WHERE encerrado_em IS NULL;

CREATE INDEX idx_turmas_professores_historico
    ON turmas_professores (turma_id, criado_em, id);

CREATE INDEX idx_turmas_professores_professor_atual
    ON turmas_professores (professor_id)
    WHERE encerrado_em IS NULL;