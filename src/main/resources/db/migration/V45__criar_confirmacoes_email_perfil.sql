CREATE TABLE public.confirmacoes_email_perfil (
    usuario_id UUID PRIMARY KEY REFERENCES public.usuarios(id) ON DELETE CASCADE,
    novo_email VARCHAR(254) NOT NULL,
    codigo_hash VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_confirmacoes_email_perfil_prazo CHECK (expira_em > criado_em)
);