ALTER TABLE public.convites_cadastro
    ADD COLUMN cadastro_expira_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE public.convites_cadastro
    ADD CONSTRAINT ck_convites_cadastro_prazo_responsavel
        CHECK (
            cadastro_expira_em IS NULL
                OR (
                perfil = 'RESPONSAVEL'
                    AND cadastro_expira_em > criado_em
                    AND cadastro_expira_em <= expira_em
                )
            );