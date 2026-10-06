CREATE TABLE IF NOT EXISTS aceites_termo (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    versao_termo VARCHAR(40) NOT NULL,
    data_aceite TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_aceites_usuario ON aceites_termo (usuario_id);