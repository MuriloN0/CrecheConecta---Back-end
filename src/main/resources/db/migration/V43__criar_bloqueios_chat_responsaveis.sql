CREATE TABLE public.bloqueios_chat_responsaveis (
     responsavel_id UUID PRIMARY KEY REFERENCES public.usuarios(id) ON DELETE CASCADE,
     bloqueado_em TIMESTAMPTZ NOT NULL,
     expira_em TIMESTAMPTZ,
     CONSTRAINT ck_bloqueios_chat_prazo
         CHECK (expira_em IS NULL OR expira_em > bloqueado_em)
);