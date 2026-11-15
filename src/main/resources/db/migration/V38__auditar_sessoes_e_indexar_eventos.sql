CREATE INDEX idx_auditoria_instante_id
    ON auditoria (instante DESC, id DESC);

CREATE FUNCTION registrar_auditoria_sessao() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$
DECLARE
evento VARCHAR(60);
BEGIN
    IF TG_OP = 'INSERT' THEN
        evento := 'SESSAO_INICIADA';
    ELSIF OLD.revogado_em IS NULL AND NEW.revogado_em IS NOT NULL THEN
        evento := 'SESSAO_REVOGADA';
ELSE
        RETURN NEW;
END IF;

INSERT INTO auditoria (id, instante, usuario_id, acao, recurso_id)
VALUES (gen_random_uuid(), CURRENT_TIMESTAMP, NEW.usuario_id, evento, NEW.id);

RETURN NEW;
END;
$$;

CREATE TRIGGER trg_auditoria_sessao
    AFTER INSERT OR UPDATE OF revogado_em ON sessoes
    FOR EACH ROW EXECUTE FUNCTION registrar_auditoria_sessao();