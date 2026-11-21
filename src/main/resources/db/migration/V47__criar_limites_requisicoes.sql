CREATE TABLE limites_requisicoes (
politica VARCHAR(40) NOT NULL,
chave_hash VARCHAR(64) NOT NULL,
quantidade INTEGER NOT NULL CHECK (quantidade > 0),
expira_em TIMESTAMPTZ NOT NULL,
PRIMARY KEY (politica, chave_hash)
);

CREATE INDEX ix_limites_requisicoes_expiracao
    ON limites_requisicoes (expira_em);