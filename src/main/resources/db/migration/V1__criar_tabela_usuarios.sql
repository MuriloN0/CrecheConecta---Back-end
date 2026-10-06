CREATE TABLE usuarios (
                          id UUID PRIMARY KEY,
                          nome VARCHAR(150) NOT NULL,
                          email VARCHAR(254) NOT NULL,
                          senha_hash VARCHAR(255) NOT NULL,
                          perfil VARCHAR(20) NOT NULL,
                          ativo BOOLEAN NOT NULL DEFAULT TRUE,
                          tentativas INTEGER NOT NULL DEFAULT 0,
                          bloqueado_ate TIMESTAMP WITH TIME ZONE,
                          versao BIGINT NOT NULL DEFAULT 0,

                          CONSTRAINT uk_usuarios_email UNIQUE (email),

                          CONSTRAINT ck_usuarios_perfil
                              CHECK (perfil IN ('DIRECAO', 'PROFESSOR', 'PAIS')),

                          CONSTRAINT ck_usuarios_tentativas
                              CHECK (tentativas >= 0),

                          CONSTRAINT ck_usuarios_email_normalizado
                              CHECK (email = LOWER(BTRIM(email)))
);