package com.deltaglobal.testebackend;

import com.deltaglobal.testebackend.domain.Agendamento;
import com.deltaglobal.testebackend.domain.EstadoAgendamento;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.AgendamentoRepository;
import com.deltaglobal.testebackend.service.AgendamentoService;
import com.deltaglobal.testebackend.service.TransferenciaService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Exercita o ciclo completo do job, inclusive a disputa de lote no PostgreSQL.
 */
@Tag("agendamento")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AgendamentoWorkflowIntegrationTest {

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
    private AgendamentoService agendamentoService;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @SpyBean
    private TransferenciaService transferenciaService;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void criarAgendamentoNaoMoveSaldoNemGeraTransferencia() {
        long saldoAntes = saldo("CONTA-001");

        Agendamento agendamento = agendamentoService.criarAgendamento(
                "CONTA-001", "CONTA-002", 5_000L, ZonedDateTime.now().plusDays(1));

        assertEquals(EstadoAgendamento.AGENDADO, agendamento.getEstado());
        assertEquals(0, agendamento.getTentativas());
        assertNull(agendamento.getTransferencia());
        assertEquals(saldoAntes, saldo("CONTA-001"));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movimento WHERE transferencia_id IS NOT NULL", Long.class));
    }

    @Test
    void deveRejeitarAgendamentoNoPassado() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                agendamentoService.criarAgendamento("CONTA-001", "CONTA-002", 5_000L,
                        ZonedDateTime.now().minusSeconds(1)));

        assertEquals("DATA_PASSADA", exception.getCodigo());
    }

    @Test
    void deveExecutarAgendamentoVencidoUmaUnicaVez() {
        Agendamento agendamento = criarEDeixarVencido(5_000L);

        agendamentoService.processarAgendamentosJob();

        Agendamento concluido = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(EstadoAgendamento.CONCLUIDO, concluido.getEstado());
        assertTrue(concluido.getTransferencia() != null);
        assertEquals(95_000L, saldo("CONTA-001"));
        assertEquals(105_000L, saldo("CONTA-002"));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));

        agendamentoService.processarAgendamentosJob();
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));
    }

    @Test
    void deveMarcarAgendamentoComoFalhadoQuandoRegraDeNegocioImpedeExecucao() {
        Agendamento agendamento = criarEDeixarVencido(100_001L);

        agendamentoService.processarAgendamentosJob();

        Agendamento falhado = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(EstadoAgendamento.FALHADO, falhado.getEstado());
        assertEquals(0, falhado.getTentativas());
        assertEquals(100_000L, saldo("CONTA-001"));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia", Long.class));
    }

    @Test
    void deveRetentarFalhaTecnicaTresVezesEEntaoFalhar() {
        Agendamento agendamento = criarEDeixarVencido(5_000L);
        doThrow(new IllegalStateException("falha tecnica"))
                .when(transferenciaService).realizarTransferencia(anyString(), anyString(), anyLong());

        agendamentoService.processarAgendamentosJob();
        assertEstadoETentativas(agendamento.getId(), EstadoAgendamento.AGENDADO, 1);

        agendamentoService.processarAgendamentosJob();
        assertEstadoETentativas(agendamento.getId(), EstadoAgendamento.AGENDADO, 2);

        agendamentoService.processarAgendamentosJob();
        assertEstadoETentativas(agendamento.getId(), EstadoAgendamento.FALHADO, 3);
    }

    @Test
    void deveCancelarSomenteAgendamentoAindaPendente() {
        Agendamento agendamento = agendamentoService.criarAgendamento(
                "CONTA-001", "CONTA-002", 5_000L, ZonedDateTime.now().plusDays(1));

        assertDoesNotThrow(() -> agendamentoService.cancelarAgendamento(agendamento.getId()));
        assertEstadoETentativas(agendamento.getId(), EstadoAgendamento.CANCELADO, 0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> agendamentoService.cancelarAgendamento(agendamento.getId()));
        assertEquals("ESTADO_INVALIDO", exception.getCodigo());
    }

    @Test
    void duasTransacoesConcorrentesDevemReivindicarLotesDisjuntos() throws Exception {
        for (int i = 0; i < 12; i++) {
            criarEDeixarVencido(1_000L);
        }

        CyclicBarrier inicioSimultaneo = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<Agendamento>> primeiro = executor.submit(() -> {
                inicioSimultaneo.await();
                return agendamentoService.pegarLoteParaProcessamento();
            });
            Future<List<Agendamento>> segundo = executor.submit(() -> {
                inicioSimultaneo.await();
                return agendamentoService.pegarLoteParaProcessamento();
            });

            List<Agendamento> loteUm = primeiro.get();
            List<Agendamento> loteDois = segundo.get();
            Set<UUID> idsUm = ids(loteUm);
            Set<UUID> idsDois = ids(loteDois);

            Set<UUID> intersecao = new HashSet<>(idsUm);
            intersecao.retainAll(idsDois);
            assertTrue(intersecao.isEmpty(), "Um agendamento nao pode ser reivindicado duas vezes");
            assertEquals(12, idsUm.size() + idsDois.size());
            assertEquals(12L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM agendamento WHERE estado = 'PROCESSANDO'", Long.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private Agendamento criarEDeixarVencido(long valorCentavos) {
        Agendamento agendamento = agendamentoService.criarAgendamento(
                "CONTA-001", "CONTA-002", valorCentavos, ZonedDateTime.now().plusDays(1));
        jdbcTemplate.update("UPDATE agendamento SET executar_em = NOW() - INTERVAL '1 minute' WHERE id = ?",
                agendamento.getId());
        return agendamento;
    }

    private Set<UUID> ids(List<Agendamento> agendamentos) {
        Set<UUID> ids = new HashSet<>();
        for (Agendamento agendamento : agendamentos) {
            ids.add(agendamento.getId());
        }
        return ids;
    }

    private void assertEstadoETentativas(UUID id, EstadoAgendamento estado, int tentativas) {
        Agendamento agendamento = agendamentoRepository.findById(id).orElseThrow();
        assertEquals(estado, agendamento.getEstado());
        assertEquals(tentativas, agendamento.getTentativas());
    }

    private Long saldo(String numeroConta) {
        return jdbcTemplate.queryForObject(
                "SELECT saldo_centavos FROM conta WHERE numero = ?", Long.class, numeroConta);
    }
}
