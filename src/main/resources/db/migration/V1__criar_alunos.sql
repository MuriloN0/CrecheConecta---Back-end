CREATE TABLE alunos (
                        id UUID PRIMARY KEY,
                        nome VARCHAR(150) NOT NULL,
                        endereco VARCHAR(300),
                        email_contato VARCHAR(254) NOT NULL,
                        telefone_contato VARCHAR(15) NOT NULL,
                        ativo BOOLEAN NOT NULL,
                        versao BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE aluno_responsaveis (
                                    aluno_id UUID NOT NULL,
                                    ordem INTEGER NOT NULL,
                                    nome VARCHAR(150) NOT NULL,
                                    parentesco VARCHAR(50) NOT NULL,
                                    email VARCHAR(254),
                                    telefone VARCHAR(15) NOT NULL,

                                    CONSTRAINT pk_aluno_responsaveis
                                        PRIMARY KEY (aluno_id, ordem),

                                    CONSTRAINT fk_responsavel_aluno
                                        FOREIGN KEY (aluno_id)
                                            REFERENCES alunos(id),

                                    CONSTRAINT ck_responsavel_ordem
                                        CHECK (ordem BETWEEN 0 AND 4)
);

CREATE INDEX idx_alunos_nome_id
    ON alunos(nome, id);