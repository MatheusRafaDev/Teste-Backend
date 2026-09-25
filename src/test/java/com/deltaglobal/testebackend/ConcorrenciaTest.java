package com.deltaglobal.testebackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.junit.jupiter.api.Disabled;

@Disabled("Testcontainers requires specific Docker setup on this environment")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConcorrenciaTest {

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
        restTemplate.delete("/dados");
    }

    @Test
    void testA_to_B_and_B_to_A_Concurrency() throws Exception {
        int numThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        Callable<ResponseEntity<String>> t1 = () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            String body = "{\"contaOrigem\":\"CONTA-001\", \"contaDestino\":\"CONTA-002\", \"valor\":100.00}";
            return restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        };

        Callable<ResponseEntity<String>> t2 = () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            String body = "{\"contaOrigem\":\"CONTA-002\", \"contaDestino\":\"CONTA-001\", \"valor\":100.00}";
            return restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        };

        List<Future<ResponseEntity<String>>> futures = executorService.invokeAll(List.of(t1, t2));
        
        for (Future<ResponseEntity<String>> f : futures) {
            assertEquals(HttpStatus.CREATED, f.get().getStatusCode());
        }
    }

    @Test
    void testIdempotenciaConcorrente() throws Exception {
        int numThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        String idempotencyKey = UUID.randomUUID().toString();
        String body = "{\"contaOrigem\":\"CONTA-003\", \"contaDestino\":\"CONTA-004\", \"valor\":50.00}";

        Callable<ResponseEntity<String>> task = () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", idempotencyKey);
            headers.set("Content-Type", "application/json");
            return restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        };

        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            tasks.add(task);
        }

        List<Future<ResponseEntity<String>>> futures = executorService.invokeAll(tasks);

        int countCreated = 0;
        int countOk = 0;

        for (Future<ResponseEntity<String>> f : futures) {
            HttpStatus status = HttpStatus.valueOf(f.get().getStatusCode().value());
            if (status == HttpStatus.CREATED) countCreated++;
            if (status == HttpStatus.OK) countOk++;
        }

        assertEquals(1, countCreated, "Apenas uma requisição deve criar");
        assertEquals(4, countOk, "As outras 4 devem retornar a resposta original com 200 OK");
    }
}
