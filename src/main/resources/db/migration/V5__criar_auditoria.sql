CREATE TABLE auditoria (
                           id UUID PRIMARY KEY,
                           instante TIMESTAMP WITH TIME ZONE NOT NULL,
                           usuario_id UUID,
                           acao VARCHAR(60) NOT NULL,
                           recurso_id UUID
);

CREATE INDEX idx_auditoria_instante ON auditoria (instante);
CREATE INDEX idx_auditoria_usuario ON auditoria (usuario_id);
