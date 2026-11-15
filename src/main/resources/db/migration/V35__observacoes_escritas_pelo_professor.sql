-- Observacoes do aluno passam a ser escritas pelo PROFESSOR da turma (antes: pelo responsavel).
-- O responsavel e a direcao passam a apenas ler.
-- As observacoes antigas foram escritas por responsaveis e nao tem professor autor:
-- sao apagadas (decisao do grupo), para a coluna autor_id nascer obrigatoria.
DELETE FROM observacoes;

ALTER TABLE observacoes
    ADD COLUMN autor_id UUID NOT NULL
        CONSTRAINT fk_observacoes_autor REFERENCES usuarios (id);

CREATE INDEX idx_observacoes_autor_id ON observacoes (autor_id);
