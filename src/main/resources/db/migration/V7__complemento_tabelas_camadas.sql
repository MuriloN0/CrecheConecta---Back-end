CREATE TABLE IF NOT EXISTS fichas_saude (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    observacoes TEXT,
    data_criacao TIMESTAMPTZ NOT NULL,
    data_atualizacao TIMESTAMPTZ NOT NULL,
    versao BIGINT
);

CREATE INDEX IF NOT EXISTS idx_fichas_saude_aluno_id ON fichas_saude (aluno_id);

CREATE TABLE IF NOT EXISTS fichas_saude_anexos (
    id UUID PRIMARY KEY,
    ficha_id UUID NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    chave_armazenamento VARCHAR(500) NOT NULL,
    tipo_conteudo VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    CONSTRAINT fk_anexo_ficha FOREIGN KEY (ficha_id)
        REFERENCES fichas_saude (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_anexos_ficha_id ON fichas_saude_anexos (ficha_id);

CREATE TABLE IF NOT EXISTS auditoria (
    id UUID PRIMARY KEY,
    instante TIMESTAMPTZ NOT NULL,
    usuario_id UUID,
    acao VARCHAR(60) NOT NULL,
    recurso_id UUID
);

CREATE INDEX IF NOT EXISTS idx_auditoria_instante ON auditoria (instante);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON auditoria (usuario_id);

CREATE TABLE IF NOT EXISTS aceites_termo (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    versao_termo VARCHAR(40) NOT NULL,
    data_aceite TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_aceites_usuario ON aceites_termo (usuario_id);

CREATE TABLE IF NOT EXISTS atividades (
    id UUID PRIMARY KEY,
    turma_id UUID NOT NULL,
    professor_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descricao TEXT,
    data_criacao TIMESTAMPTZ NOT NULL,
    prazo_conclusao DATE
);

CREATE INDEX IF NOT EXISTS idx_atividades_turma_id ON atividades (turma_id);
CREATE INDEX IF NOT EXISTS idx_atividades_tipo ON atividades (tipo);

CREATE TABLE IF NOT EXISTS conclusao_atividade (
    id UUID PRIMARY KEY,
    atividade_id UUID NOT NULL,
    aluno_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    marcada_em TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_conclusao_atividade_atividade_id ON conclusao_atividade (atividade_id);
