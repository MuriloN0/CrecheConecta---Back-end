-- Diário de Rotina Digital: acrescenta a higiene do aluno no dia (parte que o professor registra).
-- Os diários que já existiam não tinham esse campo; por isso a coluna nasce com um valor padrão
-- e o padrão é removido logo em seguida, para que todo diário NOVO precise informar a higiene.
ALTER TABLE diarios_rotina
    ADD COLUMN higiene VARCHAR(20) NOT NULL DEFAULT 'HIGIENIZADO';

ALTER TABLE diarios_rotina
    ALTER COLUMN higiene DROP DEFAULT;

ALTER TABLE diarios_rotina
    ADD CONSTRAINT ck_diarios_higiene
        CHECK (higiene IN ('HIGIENIZADO', 'PARCIAL', 'NAO_REALIZADO'));
