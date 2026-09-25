package com.deltaglobal.testebackend.controller;

import com.deltaglobal.testebackend.domain.Movimento;
import com.deltaglobal.testebackend.domain.Transferencia;
import com.deltaglobal.testebackend.dto.DepositoRequest;
import com.deltaglobal.testebackend.dto.ExtratoResponse;
import com.deltaglobal.testebackend.dto.MovimentoDto;
import com.deltaglobal.testebackend.repository.ContaRepository;
import com.deltaglobal.testebackend.repository.MovimentoRepository;
import com.deltaglobal.testebackend.service.DepositoService;
import com.deltaglobal.testebackend.service.IdempotenciaExecutor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller responsável pelas operações diretas na Conta.
 * 
 * Este controller expõe:
 * 1. Depósitos em conta (com suporte a idempotência).
 * 2. Consulta de extrato (paginado e filtrado por data).
 */
@RestController
@RequestMapping("/contas/{numero}")
public class ContaController {

    private static final Logger log = LoggerFactory.getLogger(ContaController.class);

    private final DepositoService depositoService;
    private final IdempotenciaExecutor idempotenciaExecutor;
    private final MovimentoRepository movimentoRepository;
    private final ContaRepository contaRepository;

    public ContaController(DepositoService depositoService, IdempotenciaExecutor idempotenciaExecutor, MovimentoRepository movimentoRepository, ContaRepository contaRepository) {
        this.depositoService = depositoService;
        this.idempotenciaExecutor = idempotenciaExecutor;
        this.movimentoRepository = movimentoRepository;
        this.contaRepository = contaRepository;
    }

    /**
     * Endpoint para realizar um depósito em uma conta específica.
     * O dinheiro depositado sai da conta sistema SISTEMA-ENTRADA.
     *
     * @param numero         Número da conta que vai receber o dinheiro.
     * @param idempotencyKey Chave única para evitar duplo depósito em retentativas.
     * @param request        Objeto com o valor do depósito.
     * @return 201 Created em caso de sucesso, ou 200 se a chave já foi usada.
     */
    @PostMapping("/depositos")
    public ResponseEntity<?> depositar(
            @PathVariable String numero,
            @Parameter(example = "idmp-deposito-123") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositoRequest request) {

        log.info("[REQUEST] POST /contas/{}/depositos | Key={} | Valor R$ {}", numero, idempotencyKey, request.getValor());

        return idempotenciaExecutor.execute(idempotencyKey, "POST /contas/" + numero + "/depositos", request, () -> {
            long valorCentavos = request.getValor().multiply(new BigDecimal(100)).longValue();
            Transferencia t = depositoService.depositar(numero, valorCentavos);
            
            Map<String, Object> response = new HashMap<>();
            response.put("transferenciaId", t.getId());
            response.put("valor", request.getValor());
            response.put("concluidaEm", t.getConcluidaEm());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        });
    }

    /**
     * Consulta o extrato de movimentações de uma conta.
     * 
     * As movimentações retornam ordenadas pela "sequência" decrescente, garantindo
     * a visualização da mais recente para a mais antiga, sem falhas de numeração.
     *
     * @param numero O número da conta a ser consultada.
     * @param de     Data inicial (opcional).
     * @param ate    Data final (opcional).
     * @param page   Página atual (0-indexed).
     * @param size   Quantidade de registros por página.
     * @return 200 OK com o extrato paginado e o saldo mais recente.
     */
    @GetMapping("/extrato")
    public ResponseEntity<ExtratoResponse> getExtrato(
            @PathVariable String numero,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime ate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("[REQUEST] GET /contas/{}/extrato | de={} | ate={} | page={} | size={}", numero, de, ate, page, size);

        var contaOpt = contaRepository.findByNumero(numero);
        if (contaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var conta = contaOpt.get();

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sequencia"));
        Page<Movimento> movimentosPage;

        if (de != null && ate != null) {
            movimentosPage = movimentoRepository.findByContaIdAndCriadoEmBetween(conta.getId(), de, ate, pageRequest);
        } else if (de != null) {
            movimentosPage = movimentoRepository.findByContaIdAndCriadoEmGreaterThanEqual(conta.getId(), de, pageRequest);
        } else if (ate != null) {
            movimentosPage = movimentoRepository.findByContaIdAndCriadoEmLessThanEqual(conta.getId(), ate, pageRequest);
        } else {
            // Nenhuma data fornecida
            movimentosPage = movimentoRepository.findByContaId(conta.getId(), pageRequest);
        }

        // Converte do modelo de Domínio (centavos) para o DTO de Resposta (BigDecimal com 2 casas)
        ExtratoResponse response = new ExtratoResponse();
        response.setSaldoAtual(BigDecimal.valueOf(conta.getSaldoCentavos()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
        
        response.setMovimentos(movimentosPage.getContent().stream().map(m -> {
            MovimentoDto dto = new MovimentoDto();
            dto.setMovimentoId(m.getId());
            dto.setTipo(m.getTipo().name());
            dto.setValor(BigDecimal.valueOf(m.getValorCentavos()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
            dto.setSaldoApos(BigDecimal.valueOf(m.getSaldoAposCentavos()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
            dto.setData(m.getCriadoEm());
            if (m.getTransferencia() != null) {
                dto.setTransferenciaId(m.getTransferencia().getId());
            }
            return dto;
        }).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }
}
