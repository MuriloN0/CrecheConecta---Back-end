CREATE SEQUENCE public.termos_identificador_seq AS bigint
    START WITH 1
    MINVALUE 1
    NO CYCLE;

WITH identificadores AS (
    SELECT identificador FROM public.termos_versoes
    UNION ALL
    SELECT versao_termo FROM public.aceites_termo
),
     numeros AS (
         SELECT substring(lower(identificador) FROM '(?:^|[-_.])v([0-9]+)$')::numeric AS numero
         FROM identificadores
     ),
     maior AS (
         SELECT coalesce(max(numero), 0)::bigint AS numero
         FROM numeros
         WHERE numero BETWEEN 1 AND 9223372036854775807
     )
SELECT setval(
               'public.termos_identificador_seq',
               greatest(numero, 1),
               numero > 0
       )
FROM maior;