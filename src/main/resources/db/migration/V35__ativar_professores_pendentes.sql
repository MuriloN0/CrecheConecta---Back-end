WITH professores_ativados AS (
UPDATE usuarios
SET situacao_cadastro = 'APROVADO',
    ativo = TRUE,
    versao = versao + 1
WHERE perfil = 'PROFESSOR'
  AND situacao_cadastro = 'PENDENTE_APROVACAO'
    RETURNING id
)
INSERT INTO auditoria (id, instante, usuario_id, acao, recurso_id)
SELECT gen_random_uuid(), CURRENT_TIMESTAMP, NULL, 'PROFESSOR_ATIVADO_MIGRACAO', id
FROM professores_ativados;