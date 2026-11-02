ALTER TABLE fichas_saude
    ADD CONSTRAINT fk_fichas_saude_aluno
        FOREIGN KEY (aluno_id)
            REFERENCES alunos (id)
            ON DELETE RESTRICT
    NOT VALID;