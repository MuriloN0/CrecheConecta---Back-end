-- LGPD: anonimização dos dados do aluno e do responsável quando a criança sai da creche
-- (feita pela direção, com o aluno já inativo). Marca quando aconteceu, para não repetir
-- e para a tela da direção mostrar que o cadastro foi anonimizado.
ALTER TABLE alunos ADD COLUMN anonimizado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE usuarios ADD COLUMN anonimizado_em TIMESTAMP WITH TIME ZONE;
