package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Idempotencia;
import com.deltaglobal.testebackend.domain.IdempotenciaId;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class IdempotenciaExecutorTest {

    @Test
    void reenvioComMesmoCorpoRetorna200SemExecutarOperacaoNovamente() {
        IdempotenciaService idempotenciaService = mock(IdempotenciaService.class);
        String chave = "chave";
        String endpoint = "POST /transferencias";
        Map<String, Object> request = Map.of("valor", "10.00");
        Idempotencia existente = new Idempotencia();
        existente.setId(new IdempotenciaId(chave, endpoint));
        existente.setHashRequisicao("hash-do-corpo");
        existente.setRespostaJson("{\"transferenciaId\":\"abc\"}");
        existente.setStatusHttp(HttpStatus.CREATED.value());
        when(idempotenciaService.hashRequest(request)).thenReturn("hash-do-corpo");
        when(idempotenciaService.buscarIdempotenciaExistente(chave, endpoint)).thenReturn(existente);

        IdempotenciaExecutor executor = new IdempotenciaExecutor(
                idempotenciaService,
                new ObjectMapper(),
                mock(MeterRegistry.class),
                new TransactionTemplate());
        AtomicBoolean executou = new AtomicBoolean();

        ResponseEntity<?> replay = executor.execute(chave, endpoint, request, () -> {
            executou.set(true);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of());
        });

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertFalse(executou.get());
    }
}
