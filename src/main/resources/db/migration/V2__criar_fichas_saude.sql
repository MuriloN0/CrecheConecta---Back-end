CREATE TABLE fichas_saude (
                              id UUID PRIMARY KEY,
                              aluno_id UUID NOT NULL,
                              nome VARCHAR(120) NOT NULL,
                              observacoes TEXT,
                              data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
                              data_atualizacao TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_fichas_saude_aluno_id ON fichas_saude (aluno_id);

CREATE TABLE fichas_saude_anexos (
                                     id UUID PRIMARY KEY,
                                     ficha_id UUID NOT NULL,
                                     nome_arquivo VARCHAR(255) NOT NULL,
                                     chave_armazenamento VARCHAR(500) NOT NULL,
                                     tipo_conteudo VARCHAR(100) NOT NULL,
                                     tamanho_bytes BIGINT NOT NULL,
                                     CONSTRAINT fk_anexo_ficha FOREIGN KEY (ficha_id)
                                         REFERENCES fichas_saude (id) ON DELETE CASCADE
);

CREATE INDEX idx_anexos_ficha_id ON fichas_saude_anexos (ficha_id);
