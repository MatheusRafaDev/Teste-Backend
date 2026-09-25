package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Idempotencia;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.Supplier;

/**
 * Executor central de idempotência para operações financeiras.
 *
 * COMO FUNCIONA:
 * Este componente envolve ("wraps") qualquer operação de mutação de estado
 * com a proteção de idempotência. O fluxo é:
 *
 *   1. Calcula o hash do corpo da requisição.
 *   2. Busca um registro existente na tabela `idempotencia` para (chave, endpoint).
 *   3a. Se EXISTE e o hash BATE: retorna a resposta original (HTTP 200). SEM NOVA OPERAÇÃO.
 *   3b. Se EXISTE mas o hash DIVERGE: retorna 409 CONFLICT (mesmo chave, corpo diferente).
 *   3c. Se NÃO EXISTE: executa a operação real, grava o resultado na tabela.
 *
 * CONCORRÊNCIA (Race Condition):
 * Se duas threads com a mesma chave chegam simultaneamente e nenhuma encontra
 * registro (passo 2 retorna null para ambas), ambas tentarão inserir. A constraint
 * UNIQUE (chave, endpoint) no banco garante que apenas UMA inserção vai passar.
 * A outra recebe DataIntegrityViolationException, que é capturada no catch e
 * relida do banco para retornar a resposta já salva.
 *
 * IMPORTANTE: O registro de idempotência é gravado NA MESMA TRANSAÇÃO que a operação
 * financeira. Se a transação falhar por qualquer motivo, o registro não fica salvo,
 * garantindo que não haja situação onde "o dinheiro saiu mas a chave não ficou".
 */
@Service
public class IdempotenciaExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotenciaExecutor.class);

    private final IdempotenciaService idempotenciaService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    public IdempotenciaExecutor(IdempotenciaService idempotenciaService, ObjectMapper objectMapper, MeterRegistry meterRegistry, TransactionTemplate transactionTemplate) {
        this.idempotenciaService = idempotenciaService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Executa uma operação com proteção de idempotência.
     *
     * @param chave       O valor do header Idempotency-Key (UUID gerado pelo cliente).
     * @param endpoint    Identificador do endpoint (ex: "POST /transferencias").
     * @param requestBody O corpo da requisição para fins de hash.
     * @param operation   A operação real a ser executada se não houver idempotência.
     * @param <T>         Tipo do corpo da resposta.
     * @return ResponseEntity com a resposta original (200) ou nova (201).
     */
    public <T> ResponseEntity<?> execute(String chave, String endpoint, Object requestBody,
                                         Supplier<ResponseEntity<T>> operation) {
        String hash = idempotenciaService.hashRequest(requestBody);
        log.debug("[IDEMPOTENCIA] Chave={} | Endpoint={} | Hash={}", chave, endpoint, hash);

        // Verifica se já existe um registro para esta (chave, endpoint)
        Idempotencia existente = idempotenciaService.buscarIdempotenciaExistente(chave, endpoint);

        if (existente != null) {
            // Registro encontrado: valida se o body é o mesmo
            if (!existente.getHashRequisicao().equals(hash)) {
                log.warn("[IDEMPOTENCIA] CONFLITO: Chave={} reutilizada com corpo diferente para endpoint={}", chave, endpoint);
                throw new BusinessException(HttpStatus.CONFLICT, "CHAVE_EM_USO",
                        "Chave de idempotência já utilizada com corpo diferente");
            }
            // Mesmo corpo: retorna a resposta original sem executar novamente
            log.info("[IDEMPOTENCIA] Chave={} já processada. Retornando resposta em cache (HTTP {}).",
                    chave, existente.getStatusHttp());
            return buildResponse(existente);
        }

        try {
            // Executa a operação real e persiste o registro na mesma transação para garantir atomicidade
            ResponseEntity<T> response = transactionTemplate.execute(status -> {
                ResponseEntity<T> res = operation.get();
                idempotenciaService.registrarIdempotencia(chave, endpoint, hash, res.getBody(), res.getStatusCode().value());
                return res;
            });
            log.info("[IDEMPOTENCIA] Operação concluída. Chave={} registrada para endpoint={}", chave, endpoint);
            return response;

        } catch (DataIntegrityViolationException e) {
            meterRegistry.counter("concurrency.conflicts", "endpoint", endpoint).increment();
            // Condição de corrida: outra thread inseriu a chave exatamente ao mesmo tempo.
            // Relemos o banco para retornar o que a outra thread gravou.
            log.warn("[IDEMPOTENCIA] Colisão de inserção simultânea para Chave={}. Relendo banco...", chave);
            Idempotencia inseridaPorOutraThread = idempotenciaService.buscarIdempotenciaExistente(chave, endpoint);

            if (inseridaPorOutraThread != null) {
                if (!inseridaPorOutraThread.getHashRequisicao().equals(hash)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "CHAVE_EM_USO",
                            "Chave de idempotência já utilizada com corpo diferente");
                }
                return buildResponse(inseridaPorOutraThread);
            }
            // Caso impossível na prática, mas tratado para segurança
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_IDEMPOTENCIA",
                    "Erro na concorrência de idempotência");

        } catch (BusinessException e) {
            // Erros de negócio (saldo insuficiente, etc.) são relançados normalmente.
            // Não gravamos a idempotência em caso de falha de negócio.
            log.debug("[IDEMPOTENCIA] Operação falhou com BusinessException ({}). Chave={} não foi registrada.", e.getCodigo(), chave);
            throw e;
        }
    }

    /**
     * Reconstrói um ResponseEntity a partir do JSON armazenado na tabela de idempotência.
     *
     * @param i O registro de idempotência com o JSON e status HTTP originais.
     * @return ResponseEntity reconstruído.
     */
    private ResponseEntity<?> buildResponse(Idempotencia i) {
        try {
            Object body = null;
            if (i.getRespostaJson() != null && !i.getRespostaJson().equals("null")) {
                // Lê o JSON salvo como um JsonNode genérico para evitar problemas de tipo
                body = objectMapper.readTree(i.getRespostaJson());
            }
            // Um replay idempotente não é uma nova criação: devolve o payload
            // original, mas sempre com 200 conforme o contrato HTTP.
            return ResponseEntity.ok(body);
        } catch (JsonProcessingException e) {
            log.error("[IDEMPOTENCIA] Erro ao desserializar resposta salva para status {}: {}", i.getStatusHttp(), e.getMessage());
            throw new RuntimeException("Erro ao ler resposta salva", e);
        }
    }
}
