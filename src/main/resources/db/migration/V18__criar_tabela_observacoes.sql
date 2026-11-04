-- Feature "Observacoes do filho".
-- O conteudo e dado sensivel de menor: fica CIFRADO em repouso
-- (AES-256-GCM via CriptografiaService), mesmo padrao da ficha de saude.
-- aluno_id referencia a tabela alunos (criada pelo modulo do Murilo na V11).

CREATE TABLE observacoes (
                             id                UUID PRIMARY KEY,
                             aluno_id          UUID NOT NULL,
                             titulo            VARCHAR(120) NOT NULL,
                             conteudo          TEXT,
                             data_criacao      TIMESTAMP WITH TIME ZONE NOT NULL,
                             data_atualizacao  TIMESTAMP WITH TIME ZONE NOT NULL,
                             versao            BIGINT,
                             CONSTRAINT fk_observacoes_aluno
                                 FOREIGN KEY (aluno_id) REFERENCES alunos (id)
);

CREATE INDEX idx_observacoes_aluno_id ON observacoes (aluno_id);
