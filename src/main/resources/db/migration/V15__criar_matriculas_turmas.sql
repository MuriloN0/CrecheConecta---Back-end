CREATE TABLE matriculas_turmas (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    turma_id UUID NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    encerrado_em TIMESTAMPTZ,

    CONSTRAINT fk_matriculas_aluno
        FOREIGN KEY (aluno_id)
            REFERENCES alunos (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_matriculas_turma
        FOREIGN KEY (turma_id)
        REFERENCES turmas (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_matriculas_datas
        CHECK (
        encerrado_em IS NULL
            OR encerrado_em >= criado_em
        )
);

CREATE UNIQUE INDEX uk_matricula_atual_aluno
    ON matriculas_turmas (aluno_id)
    WHERE encerrado_em IS NULL;

CREATE INDEX idx_matriculas_turma
    ON matriculas_turmas (turma_id, criado_em, id);