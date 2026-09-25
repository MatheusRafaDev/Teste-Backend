package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.*;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.ContaRepository;
import com.deltaglobal.testebackend.repository.TransferenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Serviço responsável pela execução de transferências entre contas.
 *
 * ESTRATÉGIA DE CONCORRÊNCIA:
 * Para evitar condições de corrida (race conditions) no saldo e no limite diário,
 * este serviço utiliza Lock Pessimista (SELECT FOR UPDATE) no banco de dados.
 *
 * Para evitar DEADLOCK em transferências cruzadas (A→B e B→A simultâneas),
 * os locks das duas contas são sempre adquiridos na mesma ordem canônica:
 * ordenamos os UUIDs das contas e fazemos um único SELECT FOR UPDATE na ordem
 * crescente. Assim, A→B e B→A sempre tentam adquirir os locks na mesma ordem,
 * eliminando a possibilidade de deadlock circular.
 *
 * FLUXO:
 * 1. Valida existência e estado das contas (SEM lock ainda).
 * 2. Adquire lock pessimista em AMBAS as contas, na ordem canônica dos UUIDs.
 * 3. Valida saldo (com os dados atualizados e lockados).
 * 4. Valida limite diário consultando movimentos no banco.
 * 5. Cria a Transferência, debita a origem, credita o destino.
 * 6. Se há taxa, debita a origem e credita SISTEMA-TAXAS.
 */
@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final ContaRepository contaRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final ContaService contaService;
    private final TaxaService taxaService;
    private final Clock clock;

    public TransferenciaService(ContaRepository contaRepository, TransferenciaRepository transferenciaRepository,
                                ContaService contaService,
                                TaxaService taxaService, Clock clock) {
        this.contaRepository = contaRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.contaService = contaService;
        this.taxaService = taxaService;
        this.clock = clock;
    }

    /**
     * Realiza uma transferência imediata de {@code valorCentavos} da conta de origem para a de destino.
     *
     * @param numOrigem     Número da conta de origem (ex: "CONTA-001").
     * @param numDestino    Número da conta de destino (ex: "CONTA-002").
     * @param valorCentavos Valor em centavos (deve ser > 0).
     * @return A Transferência criada com estado CONFIRMADA.
     */
    @Transactional
    public Transferencia realizarTransferencia(String numOrigem, String numDestino, Long valorCentavos) {
        log.info("[TRANSFERENCIA] Iniciando: {} → {} | Valor={} centavos", numOrigem, numDestino, valorCentavos);

        if (valorCentavos <= 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_INVALIDO", "Valor menor ou igual a zero");
        }

        if (numOrigem.equals(numDestino)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONTAS_IGUAIS", "Conta origem e destino são iguais");
        }

        // --- PASSO 1 e 2: Aquisição de locks em ORDEM CANÔNICA (anti-deadlock) ---
        // Ordenamos os UUIDs das contas para garantir uma ordem de lock consistente.
        log.debug("[TRANSFERENCIA] Adquirindo locks pessimistas em ordem canônica para: {} e {}", numOrigem, numDestino);
        
        Conta origemUnlocked = contaRepository.findByNumero(numOrigem)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONTA_ORIGEM_INEXISTENTE", "Conta origem não encontrada"));
        Conta destinoUnlocked = contaRepository.findByNumero(numDestino)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONTA_DESTINO_INEXISTENTE", "Conta destino não encontrada"));

        List<java.util.UUID> orderedIds = Arrays.asList(origemUnlocked.getId(), destinoUnlocked.getId());
        orderedIds.sort(java.util.UUID::compareTo);
        List<Conta> lockedContas = contaRepository.findByIdInForUpdateOrderById(orderedIds);

        Conta origem = lockedContas.stream().filter(c -> c.getId().equals(origemUnlocked.getId())).findFirst().get();
        Conta destino = lockedContas.stream().filter(c -> c.getId().equals(destinoUnlocked.getId())).findFirst().get();

        // Validação de estado centralizada
        origem.validarAtivaParaSaida();
        destino.validarAtivaParaEntrada();

        if (origem.isSistema() || destino.isSistema()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "CONTA_SISTEMA_NAO_PERMITIDA", "Contas de sistema não podem ser movimentadas manualmente");
        }

        // --- PASSO 3: Calcula taxa e valida saldo ---
        Long taxa = taxaService.calcularTaxa(valorCentavos);
        Long totalDebitado = valorCentavos + taxa;
        log.debug("[TRANSFERENCIA] Taxa calculada={} centavos | Total a debitar={} centavos", taxa, totalDebitado);

        // Contas do sistema (SISTEMA-ENTRADA) não têm restrição de saldo negativo
        if (!origem.isSistema() && origem.getSaldoCentavos() < totalDebitado) {
            log.warn("[TRANSFERENCIA RECUSADA] Saldo insuficiente em {}. Saldo={}, Necessário={}",
                    numOrigem, origem.getSaldoCentavos(), totalDebitado);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SALDO_INSUFICIENTE", "Saldo insuficiente");
        }

        // --- PASSO 4: Valida limite diário consultando o banco via agregação SQL ---
        // A soma é calculada diretamente no banco (SUM no JPQL), nunca em memória.
        // O fuso é America/Sao_Paulo conforme o README: "toda regra que fala em 'dia'
        // usa o fuso America/Sao_Paulo, não o fuso do servidor."
        if (!origem.isSistema()) {
            ZoneId zoneSP = ZoneId.of("America/Sao_Paulo");
            LocalDate hoje = LocalDate.now(clock.withZone(zoneSP));
            ZonedDateTime startOfDay = hoje.atStartOfDay(zoneSP);
            ZonedDateTime endOfDay = hoje.atTime(23, 59, 59, 999_999_999).atZone(zoneSP);

            Long usadoHoje = transferenciaRepository.sumValorTransferidoNoDia(
                    origem.getId(),
                    Arrays.asList(EstadoTransferencia.CONFIRMADA, EstadoTransferencia.ESTORNADA),
                    startOfDay,
                    endOfDay);

            log.debug("[TRANSFERENCIA] Limite diário: Usado hoje={} centavos | Limite={} centavos",
                    usadoHoje, origem.getLimiteDiarioCentavos());

            if (usadoHoje + valorCentavos > origem.getLimiteDiarioCentavos()) {
                log.warn("[TRANSFERENCIA RECUSADA] Limite diário excedido em {}. Usado={}, Tentativa={}, Limite={}",
                        numOrigem, usadoHoje, valorCentavos, origem.getLimiteDiarioCentavos());
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "LIMITE_DIARIO_EXCEDIDO", "Limite diário excedido");
            }
        }

        // --- PASSO 5: Persiste a transferência e gera os movimentos ---
        Transferencia t = new Transferencia();
        t.setContaOrigem(origem);
        t.setContaDestino(destino);
        t.setValorCentavos(valorCentavos);
        t.setTaxaCentavos(taxa);
        t.setEstado(EstadoTransferencia.CONFIRMADA);
        t.setConcluidaEm(ZonedDateTime.now(clock));
        t = transferenciaRepository.save(t);

        // Gera os dois movimentos principais (SAIDA na origem, ENTRADA no destino)
        contaService.debitar(origem, valorCentavos, t);
        contaService.creditar(destino, valorCentavos, t);

        // --- PASSO 6: Se há taxa, gera o par de movimentos da taxa ---
        // (SAIDA na origem, ENTRADA na SISTEMA-TAXAS)
        if (taxa > 0) {
            log.debug("[TRANSFERENCIA] Processando taxa de {} centavos para SISTEMA-TAXAS", taxa);
            Conta sistemaTaxas = contaRepository.findByNumeroForUpdate("SISTEMA-TAXAS").get();
            contaService.debitar(origem, taxa, t);
            contaService.creditar(sistemaTaxas, taxa, t);
        }

        log.info("[TRANSFERENCIA CONCLUIDA] ID={} | {}→{} | Valor={} | Taxa={} | TotalDebitado={}",
                t.getId(), numOrigem, numDestino, valorCentavos, taxa, totalDebitado);
        return t;
    }
}
