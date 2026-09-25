package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Agendamento;
import com.deltaglobal.testebackend.domain.Conta;
import com.deltaglobal.testebackend.domain.EstadoAgendamento;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.AgendamentoRepository;
import com.deltaglobal.testebackend.repository.ContaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Serviço responsável por criar e processar agendamentos de transferências.
 *
 * FUNCIONAMENTO DO JOB (multi-instância seguro):
 * O método {@link #processarAgendamentosJob()} é acionado a cada 10 segundos
 * via @Scheduled. Para garantir que, com DUAS instâncias da API no ar, o mesmo
 * agendamento não seja processado duas vezes:
 *
 *   1. A query usa "SELECT ... FOR UPDATE SKIP LOCKED":
 *      - FOR UPDATE: adquire lock pessimista nas linhas retornadas.
 *      - SKIP LOCKED: pula linhas que já estão lockadas por outra instância.
 *      Resultado: cada instância pega um lote diferente de agendamentos.
 *
 *   2. O estado é imediatamente alterado para PROCESSANDO antes de executar,
 *      impedindo que outros jobs peguem o mesmo registro.
 *
 *   3. Se a instância cair durante o processamento, o registro fica com estado
 *      PROCESSANDO indefinidamente. Por isso, uma melhoria futura seria um
 *      "reaper job" que reseta agendamentos presos em PROCESSANDO há muito tempo.
 *
 * TRATAMENTO DE FALHAS:
 * - BusinessException (ex: saldo insuficiente): agendamento vai para FALHADO
 *   com o motivo registrado. Não retenta.
 * - Exceção genérica: incrementa tentativas. Com 3+ falhas vai para FALHADO.
 */
@Service
public class AgendamentoService {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);

    // Tamanho do lote de agendamentos processados por ciclo
    private static final int BATCH_SIZE = 10;
    // Número máximo de tentativas antes de falhar permanentemente
    private static final int MAX_TENTATIVAS = 3;

    private final AgendamentoRepository agendamentoRepository;
    private final ContaRepository contaRepository;
    private final TransferenciaService transferenciaService;
    private final Clock clock;
    private final ApplicationContext context;

    @org.springframework.beans.factory.annotation.Value("${app.agendamento.reaper.timeout-minutos:5}")
    private int reaperTimeoutMinutos;

    public AgendamentoService(AgendamentoRepository agendamentoRepository, ContaRepository contaRepository,
                              TransferenciaService transferenciaService, Clock clock, ApplicationContext context) {
        this.agendamentoRepository = agendamentoRepository;
        this.contaRepository = contaRepository;
        this.transferenciaService = transferenciaService;
        this.clock = clock;
        this.context = context;
    }

    /**
     * Cria um novo agendamento de transferência futura.
     * Nenhum dinheiro é movimentado neste momento.
     * Saldo e limite diário são validados apenas na hora da execução.
     *
     * @param numOrigem     Número da conta de origem.
     * @param numDestino    Número da conta de destino.
     * @param valorCentavos Valor a transferir em centavos.
     * @param executarEm    Data/hora futura para execução (fuso horário incluso).
     * @return O agendamento criado com estado AGENDADO.
     */
    @Transactional
    public Agendamento criarAgendamento(String numOrigem, String numDestino, Long valorCentavos, ZonedDateTime executarEm) {
        // Valida que a data de execução é no futuro
        if (executarEm.isBefore(ZonedDateTime.now(clock))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DATA_PASSADA", "Data de execução no passado");
        }

        Conta origem = contaRepository.findByNumero(numOrigem)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONTA_ORIGEM_INEXISTENTE", "Conta origem não encontrada"));
        Conta destino = contaRepository.findByNumero(numDestino)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONTA_DESTINO_INEXISTENTE", "Conta destino não encontrada"));

        Agendamento a = new Agendamento();
        a.setContaOrigem(origem);
        a.setContaDestino(destino);
        a.setValorCentavos(valorCentavos);
        a.setExecutarEm(executarEm);
        a.setEstado(EstadoAgendamento.AGENDADO);

        Agendamento salvo = agendamentoRepository.save(a);
        log.info("[AGENDAMENTO CRIADO] ID={} | {}→{} | Valor={} centavos | Executar em={}",
                salvo.getId(), numOrigem, numDestino, valorCentavos, executarEm);
        return salvo;
    }

    /**
     * Cancela um agendamento.
     */
    @Transactional
    public void cancelarAgendamento(java.util.UUID id) {
        Agendamento a = agendamentoRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "AGENDAMENTO_NAO_ENCONTRADO", "Agendamento não encontrado"));

        if (a.getEstado() != EstadoAgendamento.AGENDADO) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO", "Agendamento já em andamento ou concluído");
        }

        a.setEstado(EstadoAgendamento.CANCELADO);
        agendamentoRepository.save(a);
        log.info("[AGENDAMENTO CANCELADO] ID={}", id);
    }

    /**
     * Job agendado que roda a cada 10 segundos para processar transferências pendentes.
     */
    @Scheduled(fixedDelay = 10000)
    public void processarAgendamentosJob() {
        // Chamando via context.getBean() para garantir que o proxy do @Transactional atue corretamente.
        // Se chamarmos this.pegarLoteParaProcessamento(), o Spring AOP será ignorado e falhará.
        List<Agendamento> lote = context.getBean(AgendamentoService.class).pegarLoteParaProcessamento();

        if (!lote.isEmpty()) {
            log.info("[JOB AGENDAMENTOS] Encontrados {} agendamentos para processar", lote.size());
        }

        for (Agendamento a : lote) {
            try {
                processarUmAgendamento(a);
            } catch (Exception e) {
                log.error("[JOB AGENDAMENTOS] Erro inesperado ao processar agendamento ID={}: {}", a.getId(), e.getMessage());
            }
        }
    }

    /**
     * Busca um lote de agendamentos vencidos e os marca como PROCESSANDO numa transação isolada.
     */
    @Transactional
    public List<Agendamento> pegarLoteParaProcessamento() {
        List<Agendamento> agendamentos = agendamentoRepository.findForProcessing(
                EstadoAgendamento.AGENDADO,
                ZonedDateTime.now(clock),
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Agendamento a : agendamentos) {
            a.setEstado(EstadoAgendamento.PROCESSANDO);
            // Inicia os proxies para poder acessar os numeros de conta no job fora da transação
            org.hibernate.Hibernate.initialize(a.getContaOrigem());
            org.hibernate.Hibernate.initialize(a.getContaDestino());
        }
        return agendamentoRepository.saveAllAndFlush(agendamentos);
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> listarTodos() {
        List<Agendamento> agendamentos = agendamentoRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "executarEm"));
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Agendamento a : agendamentos) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", a.getId());
            map.put("contaOrigem", a.getContaOrigem().getNumero());
            map.put("contaDestino", a.getContaDestino().getNumero());
            map.put("valor", a.getValorCentavos());
            map.put("estado", a.getEstado().name());
            map.put("executarEm", a.getExecutarEm());
            map.put("tentativas", a.getTentativas());
            if (a.getTransferencia() != null) {
                map.put("transferenciaId", a.getTransferencia().getId());
            }
            result.add(map);
        }
        return result;
    }

    /**
     * Processa um único agendamento: tenta realizar a transferência correspondente.
     * Atualiza o estado do agendamento conforme o resultado.
     * Não é @Transactional para que o TransferenciaService crie sua própria transação.
     *
     * @param a O agendamento a ser processado (deve estar em estado PROCESSANDO).
     */
    public void processarUmAgendamento(Agendamento a) {
        log.debug("[AGENDAMENTO] Processando ID={} | {}→{} | Valor={} centavos",
                a.getId(), a.getContaOrigem().getNumero(), a.getContaDestino().getNumero(), a.getValorCentavos());
        try {
            // Delega para o TransferenciaService, que aplica todas as regras de negócio
            var t = transferenciaService.realizarTransferencia(
                    a.getContaOrigem().getNumero(),
                    a.getContaDestino().getNumero(),
                    a.getValorCentavos()
            );
            a.setTransferencia(t);
            a.setEstado(EstadoAgendamento.CONCLUIDO);
            log.info("[AGENDAMENTO CONCLUIDO] ID={} gerou transferência ID={}", a.getId(), t.getId());
        } catch (BusinessException e) {
            // Falha de regra de negócio (saldo insuficiente, limite diário, etc): não retenta
            a.setEstado(EstadoAgendamento.FALHADO);
            log.warn("[AGENDAMENTO FALHADO] ID={} | Motivo: {} - {}", a.getId(), e.getCodigo(), e.getMessage());
        } catch (Exception e) {
            // Falha técnica (timeout, conexão, etc): retenta até MAX_TENTATIVAS
            a.setTentativas(a.getTentativas() + 1);
            if (a.getTentativas() >= MAX_TENTATIVAS) {
                a.setEstado(EstadoAgendamento.FALHADO);
                log.error("[AGENDAMENTO FALHADO] ID={} atingiu {} tentativas. Marcando como FALHADO.", a.getId(), MAX_TENTATIVAS);
            } else {
                a.setEstado(EstadoAgendamento.AGENDADO); // Volta para AGENDADO para nova tentativa
                log.warn("[AGENDAMENTO RETRY] ID={} | Tentativa {}/{}", a.getId(), a.getTentativas(), MAX_TENTATIVAS);
            }
        }
        agendamentoRepository.save(a);
    }

    /**
     * Job agendado que resgata agendamentos presos em estado PROCESSANDO
     * (por exemplo, devido a queda da instância durante a execução).
     */
    @Scheduled(fixedDelayString = "${app.agendamento.reaper.fixed-delay:60000}")
    @Transactional
    public void reaperJob() {
        ZonedDateTime limiteTime = ZonedDateTime.now(clock).minusMinutes(reaperTimeoutMinutos);
        int rowsUpdated = agendamentoRepository.resetAgendamentosTravados(
                EstadoAgendamento.PROCESSANDO,
                EstadoAgendamento.AGENDADO,
                limiteTime,
                ZonedDateTime.now(clock)
        );
        if (rowsUpdated > 0) {
            log.info("[REAPER JOB] Resgatados {} agendamentos travados em PROCESSANDO há mais de {} minutos", rowsUpdated, reaperTimeoutMinutos);
        }
    }
}
