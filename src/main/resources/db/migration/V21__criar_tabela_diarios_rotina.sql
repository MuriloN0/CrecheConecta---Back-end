-- Diário de Rotina Digital: como foi o dia do aluno na creche.
-- O PROFESSOR registra (feature futura); o RESPONSAVEL visualiza.
-- Regra: 1 diário por aluno por dia.
CREATE TABLE diarios_rotina (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    turma_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    data DATE NOT NULL,
    alimentacao VARCHAR(20) NOT NULL,
    sono VARCHAR(20) NOT NULL,
    evacuacao BOOLEAN NOT NULL,
    humor VARCHAR(20) NOT NULL,
    observacao_professor VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_diarios_aluno
        FOREIGN KEY (aluno_id)
            REFERENCES alunos (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_diarios_turma
        FOREIGN KEY (turma_id)
            REFERENCES turmas (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_diarios_professor
        FOREIGN KEY (professor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_diarios_aluno_data
        UNIQUE (aluno_id, data),

    CONSTRAINT ck_diarios_alimentacao
        CHECK (alimentacao IN ('COMEU_TUDO', 'COMEU_BEM', 'COMEU_POUCO', 'NAO_COMEU')),

    CONSTRAINT ck_diarios_sono
        CHECK (sono IN ('DORMIU_BEM', 'DORMIU_POUCO', 'NAO_DORMIU')),

    CONSTRAINT ck_diarios_humor
        CHECK (humor IN ('FELIZ', 'CALMO', 'AGITADO', 'CHOROSO'))
);

CREATE INDEX idx_diarios_turma_data
    ON diarios_rotina (turma_id, data);
