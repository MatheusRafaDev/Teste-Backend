package com.deltaglobal.testebackend;

import com.deltaglobal.testebackend.repository.ResumoDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Casos de contrato e de regra de negócio que exigem uma aplicação completa e PostgreSQL real.
 */
@Tag("cobertura")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CoberturaAdicionalIntegracaoTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetDatabase() {
        restTemplate.delete("/dados");
    }

    @Test
    void deveRetornar400QuandoIdempotencyKeyEstaAusente() {
        HttpHeaders headers = jsonHeaders();

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/contas/CONTA-001/depositos",
                "{\"valor\":10.00}", headers);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("HEADER_OBRIGATORIO"));
    }

    @Test
    void deveRetornar400QuandoCorpoNaoAtendeValidacao() {
        ResponseEntity<String> response = post("/transferencias", "{\"contaOrigem\":\"CONTA-001\"}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("DADOS_INVALIDOS"));
    }

    @Test
    void deveRetornar400QuandoJsonEInvalido() {
        ResponseEntity<String> response = post("/transferencias", "{\"contaOrigem\":");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("DADOS_INVALIDOS"));
    }

    @Test
    void deveRetornar404ParaContaDeOrigemInexistente() {
        ResponseEntity<String> response = post("/transferencias",
                "{\"contaOrigem\":\"CONTA-INEXISTENTE\",\"contaDestino\":\"CONTA-001\",\"valor\":10.00}");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("CONTA_ORIGEM_INEXISTENTE"));
    }

    @Test
    void deveRetornar404ParaContaDeDestinoInexistente() {
        ResponseEntity<String> response = post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-INEXISTENTE\",\"valor\":10.00}");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("CONTA_DESTINO_INEXISTENTE"));
    }

    @Test
    void deveRetornar404ParaTransferenciaEAgendamentoInexistentes() {
        ResponseEntity<String> estorno = post("/transferencias/" + UUID.randomUUID() + "/estorno",
                "{\"motivo\":\"Transferencia inexistente\"}");
        ResponseEntity<String> cancelamento = restTemplate.exchange(
                "/transferencias-agendadas/" + UUID.randomUUID() + "/cancelamento",
                HttpMethod.POST, HttpEntity.EMPTY, String.class);

        assertEquals(HttpStatus.NOT_FOUND, estorno.getStatusCode());
        assertTrue(estorno.getBody().contains("TRANSFERENCIA_INEXISTENTE"));
        assertEquals(HttpStatus.NOT_FOUND, cancelamento.getStatusCode());
        assertTrue(cancelamento.getBody().contains("AGENDAMENTO_NAO_ENCONTRADO"));
    }

    @Test
    void deveRejeitarValorComMaisDeDuasCasasDecimais() {
        ResponseEntity<String> response = post("/contas/CONTA-001/depositos", "{\"valor\":10.009}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("DADOS_INVALIDOS"));
    }

    @Test
    void deveImpedirTransferenciaEntreAMesmaConta() {
        ResponseEntity<String> response = post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-001\",\"valor\":10.00}");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("CONTAS_IGUAIS"));
    }

    @Test
    void devePermitirContaBloqueadaComoDestinoERecusarContaEncerrada() {
        ResponseEntity<String> paraBloqueada = post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-006\",\"valor\":10.00}");
        ResponseEntity<String> paraEncerrada = post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-007\",\"valor\":10.00}");

        assertEquals(HttpStatus.CREATED, paraBloqueada.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, paraEncerrada.getStatusCode());
        assertTrue(paraEncerrada.getBody().contains("CONTA_DESTINO_INVALIDA"));
    }

    @Test
    void devePersistirQuatroMovimentosEDevolverTaxaNoEstorno() {
        ResponseEntity<String> transferencia = post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-002\",\"valor\":150.00}");
        assertEquals(HttpStatus.CREATED, transferencia.getStatusCode());

        UUID transferenciaId = transferenciaMaisRecente();
        assertEquals(4L, movimentosDaTransferencia(transferenciaId));
        assertEquals(84_850L, saldo("CONTA-001"));
        assertEquals(115_000L, saldo("CONTA-002"));
        assertEquals(150L, saldo("SISTEMA-TAXAS"));

        ResponseEntity<String> estorno = post("/transferencias/" + transferenciaId + "/estorno",
                "{\"motivo\":\"Teste de devolucao de taxa\"}");
        assertEquals(HttpStatus.CREATED, estorno.getStatusCode());

        UUID estornoId = jdbcTemplate.queryForObject(
                "SELECT id FROM transferencia WHERE transferencia_original_id = ?", UUID.class, transferenciaId);
        assertEquals("ESTORNADA", jdbcTemplate.queryForObject(
                "SELECT estado FROM transferencia WHERE id = ?", String.class, transferenciaId));
        assertEquals(4L, movimentosDaTransferencia(estornoId));
        assertEquals(100_000L, saldo("CONTA-001"));
        assertEquals(100_000L, saldo("CONTA-002"));
        assertEquals(0L, saldo("SISTEMA-TAXAS"));
    }

    @Test
    void deveImpedirEstornoQuandoContaQueDevolveFoiBloqueada() {
        assertEquals(HttpStatus.CREATED, post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-002\",\"valor\":10.00}").getStatusCode());
        UUID transferenciaId = transferenciaMaisRecente();
        jdbcTemplate.update("UPDATE conta SET estado = 'BLOQUEADA' WHERE numero = 'CONTA-002'");

        ResponseEntity<String> response = post("/transferencias/" + transferenciaId + "/estorno",
                "{\"motivo\":\"Conta bloqueada nao pode enviar\"}");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("CONTA_ORIGEM_INVALIDA"));
    }

    @Test
    void deveImpedirEstornoQuandoContaQueDevolveFoiEncerrada() {
        assertEquals(HttpStatus.CREATED, post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-002\",\"valor\":10.00}").getStatusCode());
        UUID transferenciaId = transferenciaMaisRecente();
        jdbcTemplate.update("UPDATE conta SET estado = 'ENCERRADA' WHERE numero = 'CONTA-002'");

        ResponseEntity<String> response = post("/transferencias/" + transferenciaId + "/estorno",
                "{\"motivo\":\"Conta encerrada nao pode enviar\"}");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("CONTA_ORIGEM_INVALIDA"));
    }

    @Test
    void deveRetornarRespostaOriginalSemDuplicarMovimentosNoReenvioSequencial() {
        String chave = UUID.randomUUID().toString();
        String body = "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-002\",\"valor\":150.00}";

        ResponseEntity<String> primeira = post("/transferencias", body, chave);
        ResponseEntity<String> segunda = post("/transferencias", body, chave);

        assertEquals(HttpStatus.CREATED, primeira.getStatusCode());
        assertEquals(HttpStatus.OK, segunda.getStatusCode());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));
        assertEquals(4L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movimento WHERE transferencia_id IS NOT NULL", Long.class));
    }

    @Test
    void devePaginarExtratoEmOrdemDecrescenteEFiltrarPorData() throws Exception {
        assertEquals(HttpStatus.CREATED, post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-002\",\"valor\":50.00}").getStatusCode());

        ResponseEntity<String> pagina = restTemplate.getForEntity(
                "/contas/CONTA-001/extrato?page=0&size=1", String.class);
        JsonNode extrato = objectMapper.readTree(pagina.getBody());
        assertEquals(HttpStatus.OK, pagina.getStatusCode());
        assertEquals(1, extrato.path("movimentos").size());
        assertEquals("SAIDA", extrato.path("movimentos").get(0).path("tipo").asText());
        assertEquals(0, new BigDecimal("950.00").compareTo(extrato.path("saldoAtual").decimalValue()));

        String deFuturo = URLEncoder.encode(ZonedDateTime.now().plusMinutes(1).toString(), StandardCharsets.UTF_8);
        ResponseEntity<String> semMovimentos = restTemplate.getForEntity(
                "/contas/CONTA-001/extrato?de=" + deFuturo, String.class);
        assertEquals(0, objectMapper.readTree(semMovimentos.getBody()).path("movimentos").size());
        assertEquals(HttpStatus.NOT_FOUND, restTemplate.getForEntity(
                "/contas/CONTA-INEXISTENTE/extrato", String.class).getStatusCode());
    }

    @Test
    void deveRetornarResumoSemTransferenciasNaJanelaDepoisDoReset() {
        ResponseEntity<ResumoDto> response = restTemplate.getForEntity("/resumo?janelaMinutos=60", ResumoDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().getContasAtivasAgora());
        assertEquals(0, response.getBody().getTransferenciasNaJanela());
        assertEquals(0, response.getBody().getValorTransferidoNaJanela().compareTo(BigDecimal.ZERO));
        assertTrue(response.getBody().isSomaDosMovimentosEhZero());
    }

    @Test
    void deveRestaurarDadosFinanceirosEIdempotenciaAoExecutarDeleteDados() {
        assertEquals(HttpStatus.CREATED, post("/transferencias",
                "{\"contaOrigem\":\"CONTA-001\",\"contaDestino\":\"CONTA-002\",\"valor\":10.00}").getStatusCode());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotencia", Long.class));

        restTemplate.delete("/dados");

        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotencia", Long.class));
        assertEquals(100_000L, saldo("CONTA-001"));
        assertEquals(12L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movimento", Long.class));
    }

    private ResponseEntity<String> post(String path, String body) {
        return post(path, body, UUID.randomUUID().toString());
    }

    private ResponseEntity<String> post(String path, String body, String idempotencyKey) {
        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return exchange(HttpMethod.POST, path, body, headers);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String body, HttpHeaders headers) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private UUID transferenciaMaisRecente() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM transferencia ORDER BY criada_em DESC LIMIT 1", UUID.class);
    }

    private Long movimentosDaTransferencia(UUID transferenciaId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimento WHERE transferencia_id = ?", Long.class, transferenciaId);
    }

    private Long saldo(String numeroConta) {
        return jdbcTemplate.queryForObject(
                "SELECT saldo_centavos FROM conta WHERE numero = ?", Long.class, numeroConta);
    }
}
