CREATE TABLE sessoes (
                         id UUID PRIMARY KEY,
                         usuario_id UUID NOT NULL,
                         token_hash VARCHAR(64) NOT NULL,
                         criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
                         expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
                         revogado_em TIMESTAMP WITH TIME ZONE,

                         CONSTRAINT fk_sessoes_usuario
                             FOREIGN KEY (usuario_id)
                                 REFERENCES usuarios (id),

                         CONSTRAINT uk_sessoes_token_hash
                             UNIQUE (token_hash),

                         CONSTRAINT ck_sessoes_expiracao
                             CHECK (expira_em > criado_em)
);

CREATE INDEX idx_sessoes_usuario
    ON sessoes (usuario_id);

CREATE INDEX idx_sessoes_expiracao
    ON sessoes (expira_em);