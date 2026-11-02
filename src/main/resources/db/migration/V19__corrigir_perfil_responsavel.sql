-- Corrige o CHECK de perfil criado na V1.
-- A V1 aceitava 'PAIS', mas o enum Perfil (entity.Perfil) foi unificado
-- para DIRECAO, PROFESSOR e RESPONSAVEL. Sem esta correção, o Postgres
-- recusa qualquer usuário RESPONSAVEL (os testes não pegam porque usam H2 sem Flyway).

-- 1) Remove a regra antiga (senão o UPDATE abaixo seria barrado por ela).
ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_perfil;

-- 2) Converte dados legados que ainda estejam como 'PAIS'.
UPDATE usuarios SET perfil = 'RESPONSAVEL' WHERE perfil = 'PAIS';

-- 3) Recria a regra com os perfis atuais do sistema.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_perfil
    CHECK (perfil IN ('DIRECAO', 'PROFESSOR', 'RESPONSAVEL'));
