CREATE TABLE public.trocas_email_perfil (
    usuario_id UUID PRIMARY KEY REFERENCES public.usuarios(id) ON DELETE CASCADE,
    id UUID NOT NULL UNIQUE,
    novo_email VARCHAR(254) NOT NULL,
    codigo_hash VARCHAR(64) NOT NULL,
    solicitado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_troca_email_prazo CHECK (expira_em > solicitado_em)
);