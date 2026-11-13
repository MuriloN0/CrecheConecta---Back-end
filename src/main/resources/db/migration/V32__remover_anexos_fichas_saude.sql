-- Os anexos das fichas de saude (informacoes relevantes) nunca foram implementados:
-- nao existe upload nem armazenamento, e nenhum servico grava nesta tabela.
-- A entidade AnexoSaude saiu do codigo; a tabela (criada na V4/V7) sai junto.
-- O indice idx_anexos_ficha_id e a FK fk_anexo_ficha sao removidos com a tabela.
DROP TABLE IF EXISTS fichas_saude_anexos;
