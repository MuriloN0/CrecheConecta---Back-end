ALTER TABLE sessoes
    ADD COLUMN renovacao_hash VARCHAR(64),
    ADD COLUMN acesso_expira_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE sessoes
    ADD CONSTRAINT uk_sessoes_renovacao_hash UNIQUE (renovacao_hash),
    ADD CONSTRAINT ck_sessoes_renovacao
        CHECK (
            (renovacao_hash IS NULL AND acesso_expira_em IS NULL)
            OR (
                renovacao_hash IS NOT NULL
                AND acesso_expira_em IS NOT NULL
                AND acesso_expira_em > criado_em
                AND acesso_expira_em <= expira_em
            )
        );