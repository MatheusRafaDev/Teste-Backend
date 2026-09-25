package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Conta;
import com.deltaglobal.testebackend.domain.EstadoConta;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.ContaRepository;
import com.deltaglobal.testebackend.repository.TransferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
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

        when(contaRepository.findByNumero("123")).thenReturn(java.util.Optional.of(origem));
        when(contaRepository.findByNumero("456")).thenReturn(java.util.Optional.of(destino));
        List<UUID> orderedIds = Arrays.asList(origem.getId(), destino.getId());
        orderedIds.sort(UUID::compareTo);
        when(contaRepository.findByIdInForUpdateOrderById(orderedIds)).thenReturn(Arrays.asList(origem, destino));

        assertThrows(BusinessException.class, () -> transferenciaService.realizarTransferencia("123", "456", 1000L));
    }

    @Test
    void deveAceitarTransferenciaQueAtingeExatamenteOLimiteDiario() {
        Conta origem = contaAtiva("CONTA-ORIGEM", 500_000L, 200_000L);
        Conta destino = contaAtiva("CONTA-DESTINO", 100_000L, 200_000L);
        prepararContasComLock(origem, destino);

        when(taxaService.calcularTaxa(50_000L)).thenReturn(0L);
        when(transferenciaRepository.sumValorTransferidoNoDia(
                eq(origem.getId()), any(), any(), any())).thenReturn(150_000L);
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> transferenciaService.realizarTransferencia(
                origem.getNumero(), destino.getNumero(), 50_000L));

        verify(transferenciaRepository).save(any());
    }

    @Test
    void deveRecusarTransferenciaQueUltrapassaOLimiteDiario() {
        Conta origem = contaAtiva("CONTA-ORIGEM", 500_000L, 200_000L);
        Conta destino = contaAtiva("CONTA-DESTINO", 100_000L, 200_000L);
        prepararContasComLock(origem, destino);

        when(taxaService.calcularTaxa(50_001L)).thenReturn(0L);
        when(transferenciaRepository.sumValorTransferidoNoDia(
                eq(origem.getId()), any(), any(), any())).thenReturn(150_000L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                transferenciaService.realizarTransferencia(origem.getNumero(), destino.getNumero(), 50_001L));

        assertEquals("LIMITE_DIARIO_EXCEDIDO", exception.getCodigo());
    }

    @Test
    void deveCalcularJanelaDoLimiteNoFusoDeSaoPauloAoVirarODia() {
        Conta origem = contaAtiva("CONTA-ORIGEM", 500_000L, 200_000L);
        Conta destino = contaAtiva("CONTA-DESTINO", 100_000L, 200_000L);
        prepararContasComLock(origem, destino);
        when(taxaService.calcularTaxa(1_000L)).thenReturn(0L);
        when(transferenciaRepository.sumValorTransferidoNoDia(eq(origem.getId()), any(), any(), any())).thenReturn(0L);
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        transferenciaService = new TransferenciaService(contaRepository, transferenciaRepository, contaService, taxaService,
                Clock.fixed(Instant.parse("2026-09-11T02:59:59Z"), ZoneId.of("UTC")));
        transferenciaService.realizarTransferencia(origem.getNumero(), destino.getNumero(), 1_000L);

        transferenciaService = new TransferenciaService(contaRepository, transferenciaRepository, contaService, taxaService,
                Clock.fixed(Instant.parse("2026-09-11T03:00:00Z"), ZoneId.of("UTC")));
        transferenciaService.realizarTransferencia(origem.getNumero(), destino.getNumero(), 1_000L);

        org.mockito.ArgumentCaptor<ZonedDateTime> inicio = org.mockito.ArgumentCaptor.forClass(ZonedDateTime.class);
        org.mockito.ArgumentCaptor<ZonedDateTime> fim = org.mockito.ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(transferenciaRepository, atLeastOnce()).sumValorTransferidoNoDia(
                eq(origem.getId()), any(), inicio.capture(), fim.capture());

        assertEquals(List.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)),
                inicio.getAllValues().stream().map(ZonedDateTime::toLocalDate).toList());
        assertEquals(ZoneId.of("America/Sao_Paulo"), inicio.getAllValues().get(0).getZone());
        assertEquals(23, fim.getAllValues().get(0).getHour());
        assertEquals(59, fim.getAllValues().get(0).getMinute());
    }

    private Conta contaAtiva(String numero, long saldoCentavos, long limiteDiarioCentavos) {
        Conta conta = new Conta();
        conta.setId(UUID.randomUUID());
        conta.setNumero(numero);
        conta.setEstado(EstadoConta.ATIVA);
        conta.setSaldoCentavos(saldoCentavos);
        conta.setLimiteDiarioCentavos(limiteDiarioCentavos);
        conta.setUsuarioId(UUID.randomUUID());
        return conta;
    }

    private void prepararContasComLock(Conta origem, Conta destino) {
        when(contaRepository.findByNumero(origem.getNumero())).thenReturn(java.util.Optional.of(origem));
        when(contaRepository.findByNumero(destino.getNumero())).thenReturn(java.util.Optional.of(destino));
        List<UUID> orderedIds = Arrays.asList(origem.getId(), destino.getId());
        orderedIds.sort(UUID::compareTo);
        when(contaRepository.findByIdInForUpdateOrderById(orderedIds)).thenReturn(Arrays.asList(origem, destino));
    }
}
