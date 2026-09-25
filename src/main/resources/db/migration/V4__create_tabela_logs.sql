CREATE TABLE log_auditoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metodo VARCHAR(10) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    status_http INT NOT NULL,
    payload_requisicao TEXT,
    payload_resposta TEXT,
    tempo_execucao_ms BIGINT NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_auditoria_criado_em ON log_auditoria(criado_em DESC);
