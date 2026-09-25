package com.deltaglobal.testebackend;

import com.deltaglobal.testebackend.domain.Agendamento;
import com.deltaglobal.testebackend.domain.EstadoAgendamento;
import com.deltaglobal.testebackend.repository.AgendamentoRepository;
import com.deltaglobal.testebackend.service.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AgendamentoReaperTest {

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
        // Configura reaper timeout = 5 min
        registry.add("app.agendamento.reaper.timeout-minutos", () -> 5);
    }

    @org.springframework.boot.test.mock.mockito.SpyBean
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private AgendamentoService agendamentoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM agendamento");
    }

    @Test
    void testReaperResgataAgendamentoTravado() {
        // Criar via service para pegar as validações e contas
        Agendamento a = agendamentoService.criarAgendamento("CONTA-001", "CONTA-002", 5000L, ZonedDateTime.now().plusDays(1));
        
        // Simular que o agendamento travou em PROCESSANDO há mais de 5 minutos
        jdbcTemplate.update("UPDATE agendamento SET estado = 'PROCESSANDO', atualizado_em = NOW() - INTERVAL '10 minutes' WHERE id = ?", a.getId());

        agendamentoService.reaperJob();

        Agendamento resgatado = agendamentoRepository.findById(a.getId()).orElseThrow();
        assertEquals(EstadoAgendamento.AGENDADO, resgatado.getEstado());
        assertEquals(1, resgatado.getTentativas());
    }

    @Test
    void testReaperIgnoraAgendamentoRecente() {
        Agendamento a = agendamentoService.criarAgendamento("CONTA-001", "CONTA-002", 5000L, ZonedDateTime.now().plusDays(1));
        
        // Simular que o agendamento entrou em PROCESSANDO há menos de 5 minutos
        jdbcTemplate.update("UPDATE agendamento SET estado = 'PROCESSANDO', atualizado_em = NOW() - INTERVAL '2 minutes' WHERE id = ?", a.getId());

        agendamentoService.reaperJob();

        Agendamento naoResgatado = agendamentoRepository.findById(a.getId()).orElseThrow();
        assertEquals(EstadoAgendamento.PROCESSANDO, naoResgatado.getEstado());
        assertEquals(0, naoResgatado.getTentativas());
    }

    @Test
    void testReaperIgnoraOutrosEstados() {
        Agendamento a = agendamentoService.criarAgendamento("CONTA-001", "CONTA-002", 5000L, ZonedDateTime.now().plusDays(1));
        
        // Simular outro estado há mais de 5 minutos
        jdbcTemplate.update("UPDATE agendamento SET estado = 'CONCLUIDO', atualizado_em = NOW() - INTERVAL '10 minutes' WHERE id = ?", a.getId());

        agendamentoService.reaperJob();

        Agendamento naoResgatado = agendamentoRepository.findById(a.getId()).orElseThrow();
        assertEquals(EstadoAgendamento.CONCLUIDO, naoResgatado.getEstado());
        assertEquals(0, naoResgatado.getTentativas());
    }

    @Test
    void testAgendamentoCrashETransacaoAtomica() {
        // Criar agendamento que vai vencer agora
        Agendamento a = agendamentoService.criarAgendamento("CONTA-001", "CONTA-002", 5000L, ZonedDateTime.now().plusDays(1));
        
        // Mudar estado para PROCESSANDO para simular o job pegando o registro
        jdbcTemplate.update("UPDATE agendamento SET estado = 'PROCESSANDO' WHERE id = ?", a.getId());
        Agendamento processando = agendamentoRepository.findById(a.getId()).orElseThrow();

        // Salvar saldo original
        Long saldoOrigemAnterior = jdbcTemplate.queryForObject("SELECT saldo_centavos FROM conta WHERE numero = 'CONTA-001'", Long.class);

        // Forçar um crash exato no momento de salvar o estado CONCLUIDO
        org.mockito.Mockito.doThrow(new RuntimeException("Simulated Crash Server Down"))
            .when(agendamentoRepository).save(org.mockito.ArgumentMatchers.argThat(
                ag -> ag.getEstado() == EstadoAgendamento.CONCLUIDO
            ));

        // Act: Processar
        // O método processarUmAgendamento vai rodar, a transferência vai acontecer no banco,
        // mas ao dar save() no Agendamento, vai dar crash.
        // O proxy @Transactional deve interceptar o crash e dar ROLLBACK em TUDO (incluindo a transferência).
        try {
            agendamentoService.processarUmAgendamento(processando);
        } catch (RuntimeException e) {
            assertEquals("Simulated Crash Server Down", e.getMessage());
        }

        // Assert: A transação foi desfeita!
        // 1. Saldo intacto
        Long saldoOrigemAtual = jdbcTemplate.queryForObject("SELECT saldo_centavos FROM conta WHERE numero = 'CONTA-001'", Long.class);
        assertEquals(saldoOrigemAnterior, saldoOrigemAtual, "O saldo não deve ter mudado pois a transferência sofreu rollback");

        // 2. O agendamento continuou em PROCESSANDO (estado pré-transação atômica)
        Agendamento aposCrash = agendamentoRepository.findById(a.getId()).orElseThrow();
        assertEquals(EstadoAgendamento.PROCESSANDO, aposCrash.getEstado(), "O agendamento continuou no estado anterior pois a atualização de CONCLUIDO sofreu rollback");
    }
}
