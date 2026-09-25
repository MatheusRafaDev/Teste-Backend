-- V2__initial_data.sql

-- System accounts
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('00000000-0000-0000-0000-000000000001', 'SISTEMA-ENTRADA', NULL, -530000, 0, 'ATIVA');

INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('00000000-0000-0000-0000-000000000002', 'SISTEMA-TAXAS', NULL, 0, 0, 'ATIVA');

-- 5 Users
INSERT INTO usuario (id, nome, cpf) VALUES ('11111111-1111-1111-1111-111111111111', 'User 1', '11111111111');
INSERT INTO usuario (id, nome, cpf) VALUES ('22222222-2222-2222-2222-222222222222', 'User 2', '22222222222');
INSERT INTO usuario (id, nome, cpf) VALUES ('33333333-3333-3333-3333-333333333333', 'User 3', '33333333333');
INSERT INTO usuario (id, nome, cpf) VALUES ('44444444-4444-4444-4444-444444444444', 'User 4', '44444444444');
INSERT INTO usuario (id, nome, cpf) VALUES ('55555555-5555-5555-5555-555555555555', 'User 5', '55555555555');

-- 5 Active Accounts
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('10000000-0000-0000-0000-000000000001', 'CONTA-001', '11111111-1111-1111-1111-111111111111', 100000, 200000, 'ATIVA');
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('20000000-0000-0000-0000-000000000001', 'CONTA-002', '22222222-2222-2222-2222-222222222222', 100000, 200000, 'ATIVA');
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('30000000-0000-0000-0000-000000000001', 'CONTA-003', '33333333-3333-3333-3333-333333333333', 100000, 200000, 'ATIVA');
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('40000000-0000-0000-0000-000000000001', 'CONTA-004', '44444444-4444-4444-4444-444444444444', 100000, 200000, 'ATIVA');
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('50000000-0000-0000-0000-000000000001', 'CONTA-005', '55555555-5555-5555-5555-555555555555', 100000, 200000, 'ATIVA');

-- 1 Blocked Account
INSERT INTO usuario (id, nome, cpf) VALUES ('66666666-6666-6666-6666-666666666666', 'User Blocked', '66666666666');
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('60000000-0000-0000-0000-000000000001', 'CONTA-006', '66666666-6666-6666-6666-666666666666', 30000, 200000, 'BLOQUEADA');

-- 1 Closed Account
INSERT INTO usuario (id, nome, cpf) VALUES ('77777777-7777-7777-7777-777777777777', 'User Closed', '77777777777');
INSERT INTO conta (id, numero, usuario_id, saldo_centavos, limite_diario_centavos, estado)
VALUES ('70000000-0000-0000-0000-000000000001', 'CONTA-007', '77777777-7777-7777-7777-777777777777', 0, 200000, 'ENCERRADA');

-- Create movements for initial loads (1000.00 for 5 accounts = 5000.00 = 500000 cents)
-- Create movements for blocked account (300.00 = 30000 cents)
-- SISTEMA-ENTRADA generates 6 SAIDA movements

INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('00000000-0000-0000-0000-000000000001', 1, 'SAIDA', 100000, -100000);
INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('10000000-0000-0000-0000-000000000001', 1, 'ENTRADA', 100000, 100000);

INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('00000000-0000-0000-0000-000000000001', 2, 'SAIDA', 100000, -200000);
INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('20000000-0000-0000-0000-000000000001', 1, 'ENTRADA', 100000, 100000);

INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('00000000-0000-0000-0000-000000000001', 3, 'SAIDA', 100000, -300000);
INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('30000000-0000-0000-0000-000000000001', 1, 'ENTRADA', 100000, 100000);

INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('00000000-0000-0000-0000-000000000001', 4, 'SAIDA', 100000, -400000);
INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('40000000-0000-0000-0000-000000000001', 1, 'ENTRADA', 100000, 100000);

INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('00000000-0000-0000-0000-000000000001', 5, 'SAIDA', 100000, -500000);
INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('50000000-0000-0000-0000-000000000001', 1, 'ENTRADA', 100000, 100000);

INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('00000000-0000-0000-0000-000000000001', 6, 'SAIDA', 30000, -530000);
INSERT INTO movimento (conta_id, sequencia, tipo, valor_centavos, saldo_apos_centavos)
VALUES ('60000000-0000-0000-0000-000000000001', 1, 'ENTRADA', 30000, 30000);
