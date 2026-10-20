CREATE TABLE convites_cadastro (
                                   id UUID PRIMARY KEY,
                                   nome VARCHAR(150) NOT NULL,
                                   email VARCHAR(254) NOT NULL,
                                   perfil VARCHAR(20) NOT NULL,
                                   token_hash VARCHAR(64) NOT NULL,
                                   criado_por UUID NOT NULL,
                                   criado_em TIMESTAMPTZ NOT NULL,
                                   expira_em TIMESTAMPTZ NOT NULL,
                                   consumido_em TIMESTAMPTZ,
                                   cancelado_em TIMESTAMPTZ,
                                   versao BIGINT NOT NULL DEFAULT 0,

                                   CONSTRAINT uk_convites_token_hash
                                       UNIQUE (token_hash),

                                   CONSTRAINT fk_convites_criado_por
                                       FOREIGN KEY (criado_por) REFERENCES usuarios(id),

                                   CONSTRAINT ck_convites_perfil
                                       CHECK (perfil IN ('DIRECAO', 'PROFESSOR', 'RESPONSAVEL')),

                                   CONSTRAINT ck_convites_validade
                                       CHECK (expira_em > criado_em),

                                   CONSTRAINT ck_convites_email_normalizado
                                       CHECK (email = LOWER(TRIM(email)))
);

CREATE UNIQUE INDEX uk_convites_email_pendente
    ON convites_cadastro (email)
    WHERE consumido_em IS NULL
      AND cancelado_em IS NULL;