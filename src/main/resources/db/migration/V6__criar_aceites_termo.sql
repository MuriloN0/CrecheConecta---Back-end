CREATE TABLE aceites_termo (
                               id UUID PRIMARY KEY,
                               usuario_id UUID NOT NULL,
                               versao_termo VARCHAR(40) NOT NULL,
                               data_aceite TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_aceites_usuario ON aceites_termo (usuario_id);