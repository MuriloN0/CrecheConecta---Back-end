CREATE TABLE comunicados (
    id UUID PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    conteudo VARCHAR(5000) NOT NULL,
    criado_por UUID NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_comunicados_criado_por
        FOREIGN KEY (criado_por) REFERENCES usuarios(id),

    CONSTRAINT ck_comunicados_titulo
         CHECK (LENGTH(TRIM(titulo)) > 0),

    CONSTRAINT ck_comunicados_conteudo
        CHECK (LENGTH(TRIM(conteudo)) > 0)
);

CREATE INDEX idx_comunicados_ativos_criado_em ON comunicados (criado_em DESC, id DESC) WHERE ativo = TRUE;