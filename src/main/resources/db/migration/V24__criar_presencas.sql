CREATE TABLE presencas (
    id UUID PRIMARY KEY,
    turma_id UUID NOT NULL REFERENCES turmas(id),
    aluno_id UUID NOT NULL REFERENCES alunos(id),
    data DATE NOT NULL,
    presente BOOLEAN NOT NULL,
    registrado_por UUID NOT NULL REFERENCES usuarios(id),
    atualizado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_presencas_turma_aluno_data
        UNIQUE (turma_id, aluno_id, data)
);