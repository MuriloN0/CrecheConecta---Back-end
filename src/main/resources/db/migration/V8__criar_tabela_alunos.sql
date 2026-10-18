-- Feature "Dados do filho" (Perfil da Crianca).
-- Tabela de alunos vinculados ao responsavel (perfil PAIS).
-- O responsavel_id e a peca-chave da seguranca: um pai so enxerga
-- os alunos cujo responsavel_id for o id dele (anti-IDOR).

CREATE TABLE alunos (
    id              UUID PRIMARY KEY,
    responsavel_id  UUID NOT NULL,
    nome            VARCHAR(150) NOT NULL,
    data_nascimento DATE NOT NULL,
    turma           VARCHAR(80),
    tipo_sanguineo  VARCHAR(5),
    como_se_acalma  VARCHAR(500),
    medos           VARCHAR(500),
    data_criacao    TIMESTAMP WITH TIME ZONE NOT NULL,
    data_atualizacao TIMESTAMP WITH TIME ZONE NOT NULL,
    versao          BIGINT,
    CONSTRAINT fk_alunos_responsavel
        FOREIGN KEY (responsavel_id) REFERENCES usuarios (id)
);

CREATE INDEX idx_alunos_responsavel_id ON alunos (responsavel_id);

-- ---------------------------------------------------------------------------
-- SEED (dados de exemplo para desenvolvimento/demonstracao).
-- Cria um usuario PAIS de teste e dois filhos vinculados a ele.
-- ON CONFLICT DO NOTHING: se o registro ja existir, a migration nao quebra.
--
-- Usuario PAIS de teste:
--   id:    33333333-3333-3333-3333-333333333333
--   email: pais@teste.com
--   senha: (hash BCrypt ficticio; use o contorno de sessao direta no banco
--           descrito no arquivo 04 para gerar um token Bearer de teste)
-- ---------------------------------------------------------------------------

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, ativo, tentativas, versao)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'Maria Silva',
    'pais@teste.com',
    '$2a$10$abcdefghijklmnopqrstuv',
    'PAIS',
    TRUE,
    0,
    0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO alunos (
    id, responsavel_id, nome, data_nascimento, turma,
    tipo_sanguineo, como_se_acalma, medos,
    data_criacao, data_atualizacao, versao
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'Lucas Silva Oliveira',
    DATE '2022-03-12',
    'Girassol - Manha',
    'O+',
    'Musica baixa ou bichinho de pelucia',
    'Barulhos altos e escuro',
    now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO alunos (
    id, responsavel_id, nome, data_nascimento, turma,
    tipo_sanguineo, como_se_acalma, medos,
    data_criacao, data_atualizacao, versao
)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '33333333-3333-3333-3333-333333333333',
    'Helena Silva Oliveira',
    DATE '2023-07-20',
    'Sementinha - Tarde',
    'A+',
    'Colo e historia antes de dormir',
    'Ficar longe da mae',
    now(), now(), 0
)
ON CONFLICT (id) DO NOTHING;
