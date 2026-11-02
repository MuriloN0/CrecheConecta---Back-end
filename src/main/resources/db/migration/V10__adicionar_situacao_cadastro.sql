ALTER TABLE usuarios
    ADD COLUMN situacao_cadastro VARCHAR(30);

UPDATE usuarios
SET situacao_cadastro = CASE
        WHEN ativo THEN 'APROVADO'
        ELSE 'INATIVO'
    END;

ALTER TABLE usuarios
    ALTER COLUMN situacao_cadastro SET NOT NULL;

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_situacao_cadastro CHECK (
    situacao_cadastro IN (
        'PENDENTE_APROVACAO',
        'APROVADO',
        'REJEITADO',
        'INATIVO')
    );

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_situacao_ativo CHECK (
        (situacao_cadastro = 'APROVADO' AND ativo = TRUE)
        OR
        (situacao_cadastro <> 'APROVADO' AND ativo = FALSE)
    );

CREATE INDEX idx_usuarios_perfil_situacao ON usuarios (perfil, situacao_cadastro);