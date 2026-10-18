-- Feature "Observacoes do filho".
-- Registro de observacoes sobre o aluno (ex.: como foi o dia, comportamento).
-- O conteudo e um dado sensivel de menor: fica CIFRADO em repouso
-- (AES-256-GCM, via CriptografiaService), mesmo padrao da ficha de saude.
-- O titulo fica legivel (serve para listar sem precisar decifrar tudo).

CREATE TABLE observacoes (
                             id                UUID PRIMARY KEY,
                             aluno_id          UUID NOT NULL,
                             titulo            VARCHAR(120) NOT NULL,
                             conteudo          TEXT,
                             data_criacao      TIMESTAMP WITH TIME ZONE NOT NULL,
                             data_atualizacao  TIMESTAMP WITH TIME ZONE NOT NULL,
                             versao            BIGINT
);

CREATE INDEX idx_observacoes_aluno_id ON observacoes (aluno_id);

-- ---------------------------------------------------------------------------
-- SEED de exemplo (desenvolvimento/demonstracao).
-- Observacoes do aluno Lucas (11111111-...), o mesmo ja usado no frontend.
-- O conteudo fica NULO aqui: o app grava sempre CIFRADO, entao o texto
-- real voce cadastra pela tela (ai entra cifrado certinho).
-- ---------------------------------------------------------------------------

INSERT INTO observacoes (
    id, aluno_id, titulo, conteudo, data_criacao, data_atualizacao, versao
)
VALUES (
           '55555555-5555-5555-5555-555555555551',
           '11111111-1111-1111-1111-111111111111',
           'Adaptacao na turma',
           NULL,
           now(), now(), 0
       )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO observacoes (
    id, aluno_id, titulo, conteudo, data_criacao, data_atualizacao, versao
)
VALUES (
           '55555555-5555-5555-5555-555555555552',
           '11111111-1111-1111-1111-111111111111',
           'Soneca da tarde',
           NULL,
           now(), now(), 0
       )
    ON CONFLICT (id) DO NOTHING;
