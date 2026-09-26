CREATE TABLE acao_verificacao (
                                      id UUID PRIMARY KEY,
                                      usuario_id UUID NOT NULL,
                                      finalidade VARCHAR(30) NOT NULL,
                                      segredo_hash VARCHAR(64) NOT NULL,
                                      criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
                                      expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
                                      tentativas INTEGER NOT NULL DEFAULT 0,
                                      consumido_em TIMESTAMP WITH TIME ZONE,
                                      invalidado_em TIMESTAMP WITH TIME ZONE,
                                      versao BIGINT NOT NULL DEFAULT 0,

                                      CONSTRAINT fk_acao_usuario
                                          FOREIGN KEY (usuario_id)
                                              REFERENCES usuarios (id),

                                      CONSTRAINT ck_acao_finalidade
                                          CHECK (
                                              finalidade IN (
                                                             'LOGIN',
                                                             'RECUPERACAO_SENHA',
                                                             'REDEFINICAO_SENHA'
                                                  )
                                              ),

                                      CONSTRAINT ck_acao_tentativas
                                          CHECK (tentativas >= 0),

                                      CONSTRAINT ck_acao_expiracao
                                          CHECK (expira_em > criado_em),

                                      CONSTRAINT ck_acao_estado
                                          CHECK (
                                              consumido_em IS NULL
                                                  OR invalidado_em IS NULL
                                              )
);

CREATE INDEX idx_acao_usuario_finalidade
    ON acao_verificacao (usuario_id, finalidade);

CREATE INDEX idx_acao_expiracao
    ON acao_verificacao (expira_em);