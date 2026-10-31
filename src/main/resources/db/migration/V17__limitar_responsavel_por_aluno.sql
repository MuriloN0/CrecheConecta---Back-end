DO $$
BEGIN
    IF EXISTS (
        SELECT aluno_id
        FROM alunos_responsaveis
        WHERE encerrado_em IS NULL
        GROUP BY aluno_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existem alunos com mais de um responsável atual. Encerre os vínculos excedentes antes de aplicar esta migration.';
END IF;
END;
$$;

CREATE UNIQUE INDEX uk_aluno_responsavel_atual
    ON alunos_responsaveis (aluno_id)
    WHERE encerrado_em IS NULL;

DROP INDEX uk_aluno_posicao_responsavel_atual;

ALTER TABLE alunos_responsaveis
DROP CONSTRAINT ck_alunos_responsaveis_posicao;

ALTER TABLE alunos_responsaveis
DROP COLUMN posicao;