package com.deltaglobal.testebackend.controller;

import com.deltaglobal.testebackend.domain.Agendamento;
import com.deltaglobal.testebackend.dto.AgendamentoRequest;
import com.deltaglobal.testebackend.service.AgendamentoService;
import com.deltaglobal.testebackend.service.IdempotenciaExecutor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller responsável pelas operações de agendamento de transferências.
 * Agendamentos não movem o saldo nem consomem o limite no momento da requisição,
 * tudo é avaliado e cobrado apenas no momento da execução pelo Job em background.
 */
@RestController
@RequestMapping("/transferencias-agendadas")
public class AgendamentoController {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoController.class);

    private final AgendamentoService agendamentoService;
    private final IdempotenciaExecutor idempotenciaExecutor;

    public AgendamentoController(AgendamentoService agendamentoService, IdempotenciaExecutor idempotenciaExecutor) {
        this.agendamentoService = agendamentoService;
        this.idempotenciaExecutor = idempotenciaExecutor;
    }

    /**
     * Agenda uma transferência futura.
     *
     * @param idempotencyKey Chave para evitar duplicidade na requisição.
     * @param request        Objeto com contas, valor e data futura de execução.
     * @return 201 Created indicando que o agendamento foi salvo para execução posterior.
     */
    @PostMapping
    public ResponseEntity<?> agendar(
            @Parameter(example = "idmp-agendamento-123") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AgendamentoRequest request) {

        log.info("[REQUEST] POST /transferencias-agendadas | Key={} | {}→{} | R$ {} para data {}", 
                idempotencyKey, request.getContaOrigem(), request.getContaDestino(), request.getValor(), request.getExecutarEm());

        return idempotenciaExecutor.execute(idempotencyKey, "POST /transferencias-agendadas", request, () -> {
            long valorCentavos = request.getValor().multiply(new BigDecimal(100)).longValue();
            Agendamento a = agendamentoService.criarAgendamento(request.getContaOrigem(), request.getContaDestino(), valorCentavos, request.getExecutarEm());
            
            Map<String, Object> response = new HashMap<>();
            response.put("agendamentoId", a.getId());
            response.put("estado", a.getEstado().name());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        });
    }

    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(agendamentoService.listarTodos());
    }

    @PostMapping("/{id}/cancelamento")
    public ResponseEntity<?> cancelar(@PathVariable java.util.UUID id) {
        agendamentoService.cancelarAgendamento(id);
        return ResponseEntity.ok().build();
    }
}
