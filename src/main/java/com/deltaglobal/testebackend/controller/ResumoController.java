package com.deltaglobal.testebackend.controller;

import com.deltaglobal.testebackend.repository.ResumoDto;
import com.deltaglobal.testebackend.repository.ResumoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller responsável pelo endpoint de Resumo do sistema.
 * Agrega e calcula totais como ticket médio, total de taxas e valida se
 * o balanço geral (soma de todo dinheiro de todas as contas) bate com zero.
 */
@RestController
@RequestMapping("/resumo")
public class ResumoController {

    private static final Logger log = LoggerFactory.getLogger(ResumoController.class);

    private final ResumoRepository resumoRepository;
    private final Clock clock;

    public ResumoController(ResumoRepository resumoRepository, Clock clock) {
        this.resumoRepository = resumoRepository;
        this.clock = clock;
    }

    /**
     * Retorna o balanço em tempo real de estatísticas globais do sistema.
     *
     * @param janelaMinutos A janela de tempo regressiva a partir de "agora".
     *                      Se for 60, pega tudo dos últimos 60 minutos.
     * @return O DTO contendo o Resumo, montado a partir das agregações JPQL.
     */
    @GetMapping
    public ResponseEntity<ResumoDto> getResumo(@RequestParam(defaultValue = "60") int janelaMinutos) {
        log.info("[REQUEST] GET /resumo | Janela={} minutos", janelaMinutos);
        ZonedDateTime fromTime = ZonedDateTime.now(clock).minusMinutes(janelaMinutos);
        ResumoDto resumo = resumoRepository.getResumo(fromTime);
        return ResponseEntity.ok(resumo);
    }
}
