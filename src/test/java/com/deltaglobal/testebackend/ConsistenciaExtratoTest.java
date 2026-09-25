package com.deltaglobal.testebackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa as invariantes de consistência do extrato imutável.
 *
 * Requisito do README (linhas 389-392):
 * "Consistência do extrato: depois de várias operações concorrentes, valem ao mesmo tempo:
 *  - a `sequencia` de cada conta não pula número nem repete;
 *  - `conta.saldo_centavos` bate com a soma dos movimentos daquela conta;
 *  - a soma de todos os movimentos de todas as contas é zero."
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ConsistenciaExtratoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        restTemplate.delete("/dados");
    }

    /**
     * Dispara 10 transferências concorrentes entre contas diferentes,
     * depois valida as 3 invariantes do extrato ao mesmo tempo.
     */
    @Test
    void testConsistenciaExtratoAposConcorrencia() throws Exception {
        int numThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        // Dispara 5 transferências CONTA-001 → CONTA-002 e 5 CONTA-002 → CONTA-001
        // Cada conta inicia com R$1.000,00. Valor de R$50 garante que todas passem.
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                HttpHeaders h = new HttpHeaders();
                h.set("Idempotency-Key", UUID.randomUUID().toString());
                h.set("Content-Type", "application/json");
                restTemplate.exchange("/transferencias", HttpMethod.POST,
                        new HttpEntity<>("{\"contaOrigem\":\"CONTA-001\", \"contaDestino\":\"CONTA-002\", \"valor\":50.00}", h),
                        String.class);
                return null;
            });
            tasks.add(() -> {
                HttpHeaders h = new HttpHeaders();
                h.set("Idempotency-Key", UUID.randomUUID().toString());
                h.set("Content-Type", "application/json");
                restTemplate.exchange("/transferencias", HttpMethod.POST,
                        new HttpEntity<>("{\"contaOrigem\":\"CONTA-002\", \"contaDestino\":\"CONTA-001\", \"valor\":50.00}", h),
                        String.class);
                return null;
            });
        }

        List<Future<Void>> futures = executorService.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get(); // propaga qualquer exceção
        }
        executorService.shutdown();

        // ======================================================================
        // INVARIANTE 1: sequencia de cada conta não pula número nem repete
        // Para cada conta, verifica que as sequências formam um conjunto contíguo
        // de 1 até N (sem buracos e sem repetições).
        // ======================================================================
        List<Map<String, Object>> contas = jdbcTemplate.queryForList(
                "SELECT id, numero FROM conta");

        for (Map<String, Object> conta : contas) {
            UUID contaId = (UUID) conta.get("id");
            String numero = (String) conta.get("numero");

            // Conta os movimentos distintos para esta conta
            Long countMovimentos = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM movimento WHERE conta_id = ?", Long.class, contaId);

            if (countMovimentos == null || countMovimentos == 0) continue;

            // Verifica se o max(sequencia) == count(*): se houver buraco, max > count
            Long maxSeq = jdbcTemplate.queryForObject(
                    "SELECT MAX(sequencia) FROM movimento WHERE conta_id = ?", Long.class, contaId);
            Long minSeq = jdbcTemplate.queryForObject(
                    "SELECT MIN(sequencia) FROM movimento WHERE conta_id = ?", Long.class, contaId);

            assertEquals(1L, minSeq,
                    "Conta " + numero + ": sequência deve começar em 1, mas começou em " + minSeq);
            assertEquals(countMovimentos, maxSeq,
                    "Conta " + numero + ": esperado max(sequencia)=" + countMovimentos
                            + " mas foi " + maxSeq + " — há buraco ou duplicata na sequência");

            // Verifica duplicatas via count distinto
            Long countDistinct = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT sequencia) FROM movimento WHERE conta_id = ?", Long.class, contaId);
            assertEquals(countMovimentos, countDistinct,
                    "Conta " + numero + ": há sequências duplicadas");
        }

        // ======================================================================
        // INVARIANTE 2: conta.saldo_centavos bate com a soma dos movimentos
        // Compara o saldo salvo em `conta` com a soma calculada dos movimentos.
        // ======================================================================
        List<Map<String, Object>> inconsistencias = jdbcTemplate.queryForList(
                """
                SELECT c.numero,
                       c.saldo_centavos AS saldo_conta,
                       COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor_centavos
                                        ELSE -m.valor_centavos END), 0) AS saldo_calculado
                FROM conta c
                LEFT JOIN movimento m ON m.conta_id = c.id
                GROUP BY c.id, c.numero, c.saldo_centavos
                HAVING c.saldo_centavos != COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor_centavos
                                                             ELSE -m.valor_centavos END), 0)
                """);

        assertTrue(inconsistencias.isEmpty(),
                "Contas com saldo inconsistente encontradas: " + inconsistencias);

        // Também verifica que o último movimento de cada conta reflete o saldo atual
        List<Map<String, Object>> ultimoMovInconsistente = jdbcTemplate.queryForList(
                """
                SELECT c.numero,
                       c.saldo_centavos AS saldo_conta,
                       m_ultimo.saldo_apos_centavos AS saldo_ultimo_movimento
                FROM conta c
                JOIN (
                    SELECT conta_id, saldo_apos_centavos
                    FROM movimento
                    WHERE sequencia = (
                        SELECT MAX(m2.sequencia) FROM movimento m2 WHERE m2.conta_id = movimento.conta_id
                    )
                ) m_ultimo ON m_ultimo.conta_id = c.id
                WHERE c.saldo_centavos != m_ultimo.saldo_apos_centavos
                """);

        assertTrue(ultimoMovInconsistente.isEmpty(),
                "Saldo da conta não bate com saldo_apos do último movimento: " + ultimoMovInconsistente);

        // ======================================================================
        // INVARIANTE 3: a soma de TODOS os movimentos de TODAS as contas é zero
        // Entradas - Saídas deve ser exatamente 0 (dinheiro não é criado nem perdido).
        // ======================================================================
        Long somaGlobal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN tipo = 'ENTRADA' THEN valor_centavos ELSE -valor_centavos END), 0) FROM movimento",
                Long.class);

        assertEquals(0L, somaGlobal,
                "A soma de todos os movimentos deve ser zero, mas foi: " + somaGlobal
                        + " centavos. Dinheiro foi criado ou perdido!");
    }
}
