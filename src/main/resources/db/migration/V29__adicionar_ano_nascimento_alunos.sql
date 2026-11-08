ALTER TABLE public.alunos
    ALTER COLUMN data_nascimento DROP NOT NULL,
    ADD COLUMN ano_nascimento INTEGER,
    ADD CONSTRAINT ck_alunos_nascimento_informado CHECK (
        (data_nascimento IS NOT NULL AND ano_nascimento IS NULL)
        OR (
            data_nascimento IS NULL
            AND ano_nascimento IS NOT NULL
            AND ano_nascimento BETWEEN 1900 AND 9999
        )
    );