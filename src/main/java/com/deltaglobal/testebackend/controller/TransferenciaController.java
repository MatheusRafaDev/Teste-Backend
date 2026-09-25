package com.deltaglobal.testebackend.controller;

import com.deltaglobal.testebackend.domain.Transferencia;
import com.deltaglobal.testebackend.dto.EstornoRequest;
import com.deltaglobal.testebackend.dto.TransferenciaRequest;
import com.deltaglobal.testebackend.dto.TransferenciaResponse;
import com.deltaglobal.testebackend.service.EstornoService;
import com.deltaglobal.testebackend.service.IdempotenciaExecutor;
import com.deltaglobal.testebackend.service.TransferenciaService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Controller de transferências bancárias e estornos.
 *
 * Responsabilidades deste controller:
 *  - Receber e validar os dados da requisição (via Bean Validation).
 *  - Converter o valor de BigDecimal (API) para centavos (domínio).
 *  - Delegar ao IdempotenciaExecutor, que garante a proteção de duplicidade.
 *  - Converter a resposta do domínio para o DTO de resposta.
 *
 * NENHUMA regra de negócio deve existir aqui. Toda lógica fica nos Services.
 */
@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaController.class);

    private final TransferenciaService transferenciaService;
    private final EstornoService estornoService;
    private final IdempotenciaExecutor idempotenciaExecutor;

    public TransferenciaController(TransferenciaService transferenciaService, EstornoService estornoService,
                                   IdempotenciaExecutor idempotenciaExecutor) {
        this.transferenciaService = transferenciaService;
        this.estornoService = estornoService;
        this.idempotenciaExecutor = idempotenciaExecutor;
    }

    /**
     * Realiza uma transferência imediata entre duas contas.
     *
     * @param idempotencyKey Header obrigatório para evitar duplicidade de operações.
     * @param request        Corpo com contaOrigem, contaDestino e valor.
     * @return 201 (criado) com o comprovante, ou 200 se a chave já foi usada.
     */
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transferência criada com sucesso"),
            @ApiResponse(responseCode = "200", description = "Chave de idempotência já processada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "404", description = "Conta origem ou destino não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrência ou idempotência"),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente ou limite diário excedido")
    })
    @PostMapping
    public ResponseEntity<?> transferir(
            @Parameter(example = "idmp-transf-123") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferenciaRequest request) {

        log.info("[REQUEST] POST /transferencias | Key={} | {}→{} | R$ {}",
                idempotencyKey, request.getContaOrigem(), request.getContaDestino(), request.getValor());

        return idempotenciaExecutor.execute(idempotencyKey, "POST /transferencias", request, () -> {
            // Converte de BigDecimal (reais com centavos) para Long (centavos inteiros)
            long valorCentavos = request.getValor().multiply(new BigDecimal(100)).longValue();
            Transferencia t = transferenciaService.realizarTransferencia(
                    request.getContaOrigem(), request.getContaDestino(), valorCentavos);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(t));
        });
    }

    /**
     * Solicita o estorno de uma transferência existente.
     *
     * @param id             UUID da transferência a ser estornada.
     * @param idempotencyKey Header obrigatório para evitar duplo estorno.
     * @param request        Corpo com o motivo do estorno.
     * @return 201 (criado) com o comprovante do estorno, ou 200 se chave já usada.
     */
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Estorno realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "404", description = "Transferência não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrência"),
            @ApiResponse(responseCode = "422", description = "Transferência já estornada")
    })
    @PostMapping("/{id}/estorno")
    public ResponseEntity<?> estornar(
            @PathVariable UUID id,
            @Parameter(example = "idmp-estorno-123") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EstornoRequest request) {

        log.info("[REQUEST] POST /transferencias/{}/estorno | Key={}", id, idempotencyKey);

        return idempotenciaExecutor.execute(idempotencyKey, "POST /transferencias/" + id + "/estorno", request, () -> {
            Transferencia t = estornoService.estornar(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(t));
        });
    }

    /**
     * Converte a entidade de domínio {@link Transferencia} para o DTO de resposta.
     * Faz a conversão de centavos (Long) para reais (BigDecimal com 2 casas decimais).
     */
    private TransferenciaResponse toResponse(Transferencia t) {
        TransferenciaResponse r = new TransferenciaResponse();
        r.setTransferenciaId(t.getId());
        r.setContaOrigem(t.getContaOrigem().getNumero());
        r.setContaDestino(t.getContaDestino().getNumero());
        // Converte centavos → reais com arredondamento bancário (HALF_EVEN)
        r.setValor(BigDecimal.valueOf(t.getValorCentavos()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
        r.setTaxa(BigDecimal.valueOf(t.getTaxaCentavos()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
        r.setTotalDebitado(r.getValor().add(r.getTaxa()));
        r.setSaldoOrigemApos(BigDecimal.valueOf(t.getContaOrigem().getSaldoCentavos()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
        r.setConcluidaEm(t.getConcluidaEm());
        return r;
    }
}
