CREATE TABLE IF NOT EXISTS auditoria (
    id UUID PRIMARY KEY,
    instante TIMESTAMPTZ NOT NULL,
    usuario_id UUID,
    acao VARCHAR(60) NOT NULL,
    recurso_id UUID
);

CREATE INDEX IF NOT EXISTS idx_auditoria_instante ON auditoria (instante);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON auditoria (usuario_id);