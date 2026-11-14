-- "Ficha de saúde" passa a se chamar "Informações relevantes" em todo o sistema
-- (tela, API /api/alunos/{id}/informacoes-relevantes e banco). Os dados não mudam.
ALTER TABLE fichas_saude RENAME TO informacoes_relevantes;

ALTER INDEX IF EXISTS idx_fichas_saude_aluno_id RENAME TO idx_informacoes_relevantes_aluno_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fichas_saude_pkey') THEN
        ALTER TABLE informacoes_relevantes RENAME CONSTRAINT fichas_saude_pkey TO informacoes_relevantes_pkey;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_fichas_saude_aluno') THEN
        ALTER TABLE informacoes_relevantes RENAME CONSTRAINT fk_fichas_saude_aluno TO fk_informacoes_relevantes_aluno;
    END IF;
END $$;
