package com.deltaglobal.testebackend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@Tag("trigger")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MovimentoTriggerTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void testUpdateMovimentoImpedido() {
        // Primeiro garantimos que existe um movimento pegando o ID de um dos movimentos iniciais de carga (da V2__initial_data)
        String sqlSelect = "SELECT id FROM movimento LIMIT 1";
        java.util.UUID id = jdbcTemplate.queryForObject(sqlSelect, java.util.UUID.class);
        
        // Tenta atualizar e espera que o banco recuse com o erro da trigger
        Exception exception = assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("UPDATE movimento SET valor_centavos = 99999 WHERE id = ?", id);
        });
        
        assertTrue(exception.getMessage().contains("A tabela movimento é imutável"));
    }

    @Test
    void testDeleteMovimentoImpedido() {
        // Pega um id
        String sqlSelect = "SELECT id FROM movimento LIMIT 1";
        java.util.UUID id = jdbcTemplate.queryForObject(sqlSelect, java.util.UUID.class);
        
        // Tenta deletar e espera recusa
        Exception exception = assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("DELETE FROM movimento WHERE id = ?", id);
        });
        
        assertTrue(exception.getMessage().contains("A tabela movimento é imutável"));
    }
}
