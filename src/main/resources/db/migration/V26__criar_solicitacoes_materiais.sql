CREATE TABLE solicitacoes_materiais (
    id UUID PRIMARY KEY,
    professor_id UUID NOT NULL,
    sala VARCHAR(50) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descricao VARCHAR(2000),
    prioridade VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    finalizado_em TIMESTAMPTZ,
    versao BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_solicitacoes_materiais_professor
        FOREIGN KEY (professor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_solicitacoes_materiais_sala
        CHECK (LENGTH(TRIM(sala)) > 0),

    CONSTRAINT ck_solicitacoes_materiais_titulo
        CHECK (LENGTH(TRIM(titulo)) > 0),

    CONSTRAINT ck_solicitacoes_materiais_prioridade
        CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA')),

    CONSTRAINT ck_solicitacoes_materiais_status
        CHECK (status IN ('ABERTA', 'ATENDIDA', 'RECUSADA', 'CANCELADA'))
);

CREATE INDEX idx_solicitacoes_materiais_professor
    ON solicitacoes_materiais (professor_id, criado_em DESC);

CREATE INDEX idx_solicitacoes_materiais_status
    ON solicitacoes_materiais (status, criado_em DESC);

CREATE TABLE itens_solicitacao_material (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,
    quantidade INTEGER NOT NULL,

    CONSTRAINT fk_itens_solicitacao_material
        FOREIGN KEY (solicitacao_id)
            REFERENCES solicitacoes_materiais (id)
            ON DELETE CASCADE,

    CONSTRAINT ck_itens_solicitacao_material_nome
        CHECK (LENGTH(TRIM(nome)) > 0),

    CONSTRAINT ck_itens_solicitacao_material_quantidade
        CHECK (quantidade > 0)
);

CREATE INDEX idx_itens_solicitacao_material_solicitacao
    ON itens_solicitacao_material (solicitacao_id);

CREATE TABLE mensagens_solicitacao_material (
    id UUID PRIMARY KEY,
    solicitacao_id UUID NOT NULL,
    autor_id UUID NOT NULL,
    conteudo VARCHAR(2000) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_mensagens_solicitacao_material
        FOREIGN KEY (solicitacao_id)
            REFERENCES solicitacoes_materiais (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_mensagens_solicitacao_material_autor
        FOREIGN KEY (autor_id)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_mensagens_solicitacao_material_conteudo
        CHECK (LENGTH(TRIM(conteudo)) > 0)
);

CREATE INDEX idx_mensagens_solicitacao_material_solicitacao
    ON mensagens_solicitacao_material (solicitacao_id, criado_em);
