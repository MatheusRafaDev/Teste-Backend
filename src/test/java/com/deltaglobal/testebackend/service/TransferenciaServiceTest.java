package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Conta;
import com.deltaglobal.testebackend.domain.EstadoConta;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.ContaRepository;
import com.deltaglobal.testebackend.repository.TransferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    @Mock
    private ContaRepository contaRepository;
    @Mock
    private TransferenciaRepository transferenciaRepository;
    @Mock
    private ContaService contaService;
    @Mock
    private TaxaService taxaService;
    
    private Clock clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @InjectMocks
    private TransferenciaService transferenciaService;

    @BeforeEach
    void setUp() {
        transferenciaService = new TransferenciaService(contaRepository, transferenciaRepository, contaService, taxaService, clock);
    }

    @Test
    void testTransferenciaOrigemBloqueada() {
        Conta origem = new Conta();
        origem.setId(UUID.randomUUID());
        origem.setEstado(EstadoConta.BLOQUEADA);
        origem.setNumero("123");

        Conta destino = new Conta();
        destino.setId(UUID.randomUUID());
        destino.setEstado(EstadoConta.ATIVA);
        destino.setNumero("456");

        List<String> numeros = Arrays.asList("123", "456");
        when(contaRepository.findByNumeroInForUpdateOrderByNumero(numeros)).thenReturn(Arrays.asList(origem, destino));

        assertThrows(BusinessException.class, () -> transferenciaService.realizarTransferencia("123", "456", 1000L));
    }
}
