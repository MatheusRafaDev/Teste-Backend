package com.deltaglobal.testebackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TransferenciaIntegracaoTest {

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

    @BeforeEach
    void setup() {
        // Limpa o banco de dados entre os testes restaurando a migration inicial
        restTemplate.delete("/dados");
    }

    @Test
    void testDepositoSucesso() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        String body = "{\"valor\": 250.00}";
        
        ResponseEntity<String> response = restTemplate.exchange("/contas/CONTA-001/depositos", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("transferenciaId"));
    }

    @Test
    void testTransferenciaOrigemBloqueada() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        // CONTA-006 is bloqueada in V2
        String body = "{\"contaOrigem\":\"CONTA-006\", \"contaDestino\":\"CONTA-001\", \"valor\":50.00}";
        
        ResponseEntity<String> response = restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("CONTA_ORIGEM_INVALIDA"));
    }

    @Test
    void testTransferenciaSaldoInsuficiente() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        // CONTA-001 initially has 1000.00. Transfer 2000.00 should fail
        String body = "{\"contaOrigem\":\"CONTA-001\", \"contaDestino\":\"CONTA-002\", \"valor\":2000.00}";
        
        ResponseEntity<String> response = restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody().contains("SALDO_INSUFICIENTE"));
    }
    
    @Test
    void testIdempotenciaRequisicaoDiferente() {
        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("Content-Type", "application/json");

        String body1 = "{\"valor\": 250.00}";
        String body2 = "{\"valor\": 500.00}";

        ResponseEntity<String> r1 = restTemplate.exchange("/contas/CONTA-001/depositos", HttpMethod.POST, new HttpEntity<>(body1, headers), String.class);
        assertEquals(HttpStatus.CREATED, r1.getStatusCode());

        ResponseEntity<String> r2 = restTemplate.exchange("/contas/CONTA-001/depositos", HttpMethod.POST, new HttpEntity<>(body2, headers), String.class);
        assertEquals(HttpStatus.CONFLICT, r2.getStatusCode());
        assertTrue(r2.getBody().contains("CHAVE_EM_USO"));
    }

    /**
     * README (linha 226): "Depósito em conta BLOQUEADA é aceito."
     * CONTA-006 está BLOQUEADA na carga inicial (V2__initial_data.sql).
     */
    @Test
    void testDepositoEmContaBloqueadaEAceito() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.set("Content-Type", "application/json");
        String body = "{\"valor\": 100.00}";

        ResponseEntity<String> response = restTemplate.exchange(
                "/contas/CONTA-006/depositos", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Depósito em conta BLOQUEADA deve ser aceito com 201");
        assertTrue(response.getBody().contains("transferenciaId"));
    }

    /**
     * README (linha 222): Conta ENCERRADA deve retornar 409.
     * CONTA-007 está ENCERRADA na carga inicial (V2__initial_data.sql).
     */
    @Test
    void testDepositoEmContaEncerradaRejeitado() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.set("Content-Type", "application/json");
        String body = "{\"valor\": 100.00}";

        ResponseEntity<String> response = restTemplate.exchange(
                "/contas/CONTA-007/depositos", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
                "Depósito em conta ENCERRADA deve ser rejeitado com 409");
        assertTrue(response.getBody().contains("CONTA_DESTINO_INVALIDA"));
    }
}
