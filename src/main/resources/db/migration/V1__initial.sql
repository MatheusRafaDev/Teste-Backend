CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(255) NOT NULL,
    cpf VARCHAR(14) NOT NULL UNIQUE
);

CREATE TABLE conta (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    numero VARCHAR(50) NOT NULL UNIQUE,
    usuario_id UUID REFERENCES usuario(id),
    saldo_centavos BIGINT NOT NULL DEFAULT 0,
    limite_diario_centavos BIGINT NOT NULL DEFAULT 200000,
    estado VARCHAR(20) NOT NULL
);

CREATE TABLE transferencia (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conta_origem_id UUID REFERENCES conta(id),
    conta_destino_id UUID REFERENCES conta(id),
    valor_centavos BIGINT NOT NULL,
    taxa_centavos BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    transferencia_original_id UUID REFERENCES transferencia(id),
    criada_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concluida_em TIMESTAMPTZ
);

CREATE TABLE movimento (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conta_id UUID NOT NULL REFERENCES conta(id),
    transferencia_id UUID REFERENCES transferencia(id),
    sequencia BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor_centavos BIGINT NOT NULL,
    saldo_apos_centavos BIGINT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (conta_id, sequencia)
);

CREATE TABLE agendamento (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conta_origem_id UUID NOT NULL REFERENCES conta(id),
    conta_destino_id UUID NOT NULL REFERENCES conta(id),
    valor_centavos BIGINT NOT NULL,
    executar_em TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL,
    tentativas INT NOT NULL DEFAULT 0,
    transferencia_id UUID REFERENCES transferencia(id)
);

CREATE TABLE idempotencia (
    chave VARCHAR(100) NOT NULL,
    endpoint VARCHAR(100) NOT NULL,
    hash_requisicao VARCHAR(255) NOT NULL,
    resposta_json TEXT,
    status_http INT,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chave, endpoint)
);

-- Indexes for performance
-- Extrato
CREATE INDEX idx_movimento_conta_seq ON movimento(conta_id, sequencia DESC);
-- Resumo (janelaMinutos)
CREATE INDEX idx_transferencia_criada_em ON transferencia(criada_em);
-- Agendamento processing
CREATE INDEX idx_agendamento_estado_executar_em ON agendamento(estado, executar_em);

-- Imutability of movimento via trigger
CREATE OR REPLACE FUNCTION prevent_movimento_update_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'A tabela movimento é imutável.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_movimento_update
BEFORE UPDATE ON movimento
FOR EACH ROW EXECUTE FUNCTION prevent_movimento_update_delete();

CREATE TRIGGER trg_prevent_movimento_delete
BEFORE DELETE ON movimento
FOR EACH ROW EXECUTE FUNCTION prevent_movimento_update_delete();
