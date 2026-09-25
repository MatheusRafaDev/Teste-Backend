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


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
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

    @Test
    void testEstornoConcorrenteSimultaneo() throws Exception {
        int numThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        // 1. Criar uma transferência válida primeiro
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.set("Content-Type", "application/json");
        String transferBody = "{\"contaOrigem\":\"CONTA-001\", \"contaDestino\":\"CONTA-002\", \"valor\":100.00}";
        @SuppressWarnings("rawtypes")
        ResponseEntity<java.util.Map> transferRes = restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(transferBody, headers), java.util.Map.class);
        assertEquals(HttpStatus.CREATED, transferRes.getStatusCode());
        String transfId = transferRes.getBody().get("transferenciaId").toString();

        // 2. Estornar concorrentemente
        String estornoBody = "{\"motivo\":\"Estorno de teste\"}";
        Callable<ResponseEntity<String>> task = () -> {
            HttpHeaders estornoHeaders = new HttpHeaders();
            estornoHeaders.set("Idempotency-Key", UUID.randomUUID().toString()); // chaves diferentes para forçar a concorrência na regra de negócio
            estornoHeaders.set("Content-Type", "application/json");
            return restTemplate.exchange("/transferencias/" + transfId + "/estorno", HttpMethod.POST, new HttpEntity<>(estornoBody, estornoHeaders), String.class);
        };

        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            tasks.add(task);
        }

        List<Future<ResponseEntity<String>>> futures = executorService.invokeAll(tasks);

        int countCreated = 0;
        int countConflict = 0;

        for (Future<ResponseEntity<String>> f : futures) {
            HttpStatus status = HttpStatus.valueOf(f.get().getStatusCode().value());
            if (status == HttpStatus.CREATED) countCreated++;
            if (status == HttpStatus.CONFLICT) countConflict++;
        }

        assertEquals(1, countCreated, "Apenas um estorno deve ser efetuado");
        assertEquals(4, countConflict, "Os outros 4 devem falhar por estado inválido ou duplicidade");
    }

    @Test
    void testLimiteDiarioConcorrenteEstourado() throws Exception {
        int numThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        // O limite diário é 2000. Cada conta inicia com 1000. 
        // Vamos tentar duas transferências de 600 da CONTA-001 para a CONTA-002.
        // Sozinhas elas passam, mas juntas dão 1200, que supera o saldo atual de 1000. 
        // Para testar o LIMITE DIÁRIO (2000), precisaríamos que o saldo fosse maior que o limite.
        // Vamos depositar 2000 na CONTA-001 para que ela tenha 3000 de saldo.
        HttpHeaders depHeaders = new HttpHeaders();
        depHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        depHeaders.set("Content-Type", "application/json");
        restTemplate.exchange("/contas/CONTA-001/depositos", HttpMethod.POST, new HttpEntity<>("{\"valor\":2000.00}", depHeaders), String.class);

        // Agora a conta tem 3000 de saldo, mas limite diário de 2000.
        // Duas threads vão tentar transferir 1200. Somadas (2400) estouram o limite de 2000.
        Callable<ResponseEntity<String>> task1 = () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            headers.set("Content-Type", "application/json");
            String body = "{\"contaOrigem\":\"CONTA-001\", \"contaDestino\":\"CONTA-002\", \"valor\":1200.00}";
            return restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        };

        Callable<ResponseEntity<String>> task2 = () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Idempotency-Key", UUID.randomUUID().toString());
            headers.set("Content-Type", "application/json");
            String body = "{\"contaOrigem\":\"CONTA-001\", \"contaDestino\":\"CONTA-002\", \"valor\":1200.00}";
            return restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        };

        List<Future<ResponseEntity<String>>> futures = executorService.invokeAll(List.of(task1, task2));
        
        int countCreated = 0;
        int countUnprocessable = 0;

        for (Future<ResponseEntity<String>> f : futures) {
            HttpStatus status = HttpStatus.valueOf(f.get().getStatusCode().value());
            if (status == HttpStatus.CREATED) countCreated++;
            if (status == HttpStatus.UNPROCESSABLE_ENTITY) countUnprocessable++;
        }

        assertEquals(1, countCreated, "Apenas uma transferência de 1200 deve passar");
        assertEquals(1, countUnprocessable, "A outra deve falhar por limite diário estourado");
    }

    @Test
    void testIdempotenciaConcorrenteCorpoDiferente() throws Exception {
        int numThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        String idempotencyKey = UUID.randomUUID().toString();

        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            tasks.add(() -> {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Idempotency-Key", idempotencyKey);
                headers.set("Content-Type", "application/json");
                // Corpo diferente em cada thread
                String body = "{\"contaOrigem\":\"CONTA-003\", \"contaDestino\":\"CONTA-004\", \"valor\":" + (50 + index) + ".00}";
                return restTemplate.exchange("/transferencias", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            });
        }

        List<Future<ResponseEntity<String>>> futures = executorService.invokeAll(tasks);

        int countCreated = 0;
        int countConflict = 0;

        for (Future<ResponseEntity<String>> f : futures) {
            HttpStatus status = HttpStatus.valueOf(f.get().getStatusCode().value());
            if (status == HttpStatus.CREATED) countCreated++;
            if (status == HttpStatus.CONFLICT) countConflict++;
        }

        assertEquals(1, countCreated, "Apenas uma requisição (a primeira a commitar) deve criar");
        assertEquals(4, countConflict, "As outras 4 devem dar 409 Conflict porque a chave é a mesma mas o corpo é diferente");
    }
}
