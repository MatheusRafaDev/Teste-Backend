-- V3__add_versao_conta.sql
-- Adiciona coluna 'versao' para suporte a @Version (optimistic locking) na entidade Conta.
-- O valor inicial 0 é necessário para todas as linhas existentes.
ALTER TABLE conta ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
