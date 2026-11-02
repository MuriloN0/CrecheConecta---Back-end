-- Chamada (frequência) diária do aluno.
-- O PROFESSOR registra (feature futura); o RESPONSAVEL visualiza as faltas e presenças do filho.
-- Regra: 1 registro por aluno por dia; justificativa só faz sentido quando o aluno faltou.
CREATE TABLE chamadas (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    turma_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    data DATE NOT NULL,
    presente BOOLEAN NOT NULL,
    justificativa VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_chamadas_aluno
        FOREIGN KEY (aluno_id)
            REFERENCES alunos (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_chamadas_turma
        FOREIGN KEY (turma_id)
            REFERENCES turmas (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_chamadas_professor
        FOREIGN KEY (professor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_chamadas_aluno_data
        UNIQUE (aluno_id, data),

    CONSTRAINT ck_chamadas_justificativa
        CHECK (presente = FALSE OR justificativa IS NULL)
);

CREATE INDEX idx_chamadas_turma_data
    ON chamadas (turma_id, data);
