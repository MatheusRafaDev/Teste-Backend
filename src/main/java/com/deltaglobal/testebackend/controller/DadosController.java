package com.deltaglobal.testebackend.controller;

import org.flywaydb.core.Flyway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller utilitário (exclusivo para desenvolvimento e testes).
 * Usado para limpar completamente o banco de dados e aplicar a carga inicial (migrações).
 */
@RestController
@RequestMapping("/dados")

public class DadosController {

    private static final Logger log = LoggerFactory.getLogger(DadosController.class);

    private final Flyway flyway;

    public DadosController(Flyway flyway) {
        this.flyway = flyway;
    }

    /**
     * Reseta completamente a base de dados.
     * Faz um clean drop do Flyway e em seguida um migrate.
     * 
     * ATENÇÃO: Nunca exportar ou habilitar isso em ambiente de produção real.
     */
    @DeleteMapping
    public ResponseEntity<Void> apagarDados() {
        log.info("[REQUEST] DELETE /dados | Restaurando banco de dados para a carga inicial...");
        flyway.clean();
        flyway.migrate();
        log.info("[REQUEST] DELETE /dados | Concluído.");
        return ResponseEntity.ok().build();
    }
}
