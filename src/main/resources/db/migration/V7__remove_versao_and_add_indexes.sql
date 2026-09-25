-- V7__remove_versao_and_add_indexes.sql
-- Remove coluna 'versao' que era para lock otimista não utilizado
ALTER TABLE conta DROP COLUMN IF EXISTS versao;

-- Adiciona índices mencionados no README mas ausentes nas migrations anteriores
CREATE INDEX IF NOT EXISTS idx_transferencia_limite_diario ON transferencia (conta_origem_id, estado, criada_em);
CREATE INDEX IF NOT EXISTS idx_idempotencia_chave ON idempotencia (chave, endpoint);
