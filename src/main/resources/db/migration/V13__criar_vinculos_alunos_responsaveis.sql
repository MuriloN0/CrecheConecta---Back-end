CREATE TABLE alunos_responsaveis (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    responsavel_id UUID NOT NULL,
    posicao INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    encerrado_em TIMESTAMPTZ,

    CONSTRAINT fk_alunos_responsaveis_aluno
        FOREIGN KEY (aluno_id)
        REFERENCES alunos (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_alunos_responsaveis_usuario
        FOREIGN KEY (responsavel_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_alunos_responsaveis_posicao
        CHECK (posicao BETWEEN 1 AND 5),

    CONSTRAINT ck_alunos_responsaveis_datas
        CHECK (encerrado_em IS NULL OR encerrado_em >= criado_em)
);

CREATE UNIQUE INDEX uk_responsavel_aluno_atual
    ON alunos_responsaveis (responsavel_id)
    WHERE encerrado_em IS NULL;

CREATE UNIQUE INDEX uk_aluno_posicao_responsavel_atual
    ON alunos_responsaveis (aluno_id, posicao)
    WHERE encerrado_em IS NULL;

CREATE INDEX idx_alunos_responsaveis_historico
    ON alunos_responsaveis (aluno_id, criado_em, id);