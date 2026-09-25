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
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Serviço responsável por executar o estorno de uma transferência.
 *
 * REGRA DE NEGÓCIO (Decisão em aberto nº 1 do README):
 * O estorno é RECUSADO se a conta de destino (que deve devolver o dinheiro)
 * não tiver saldo suficiente. Isso preserva a invariante de que nenhuma conta
 * de usuário pode ficar com saldo negativo.
 *
 * FLUXO:
 * 1. Busca a transferência original com lock pessimista (FOR UPDATE) para
 *    evitar que dois estornos simultâneos processem ao mesmo tempo.
 * 2. Valida que a transferência está no estado CONFIRMADA.
 * 3. Verifica se já existe um estorno ativo para esta transferência.
 * 4. Adquire locks nas contas em ordem canônica (anti-deadlock).
 * 5. Valida que o destino tem saldo para devolver.
 * 6. Altera estado da transferência original para ESTORNADA.
 * 7. Cria nova transferência de estorno (sentido inverso) e gera movimentos.
 * 8. Se a original tinha taxa, devolve a taxa também (de SISTEMA-TAXAS para a origem).
 */
@Service
public class EstornoService {

    private static final Logger log = LoggerFactory.getLogger(EstornoService.class);

    private final ContaRepository contaRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final ContaService contaService;
    private final Clock clock;

    public EstornoService(ContaRepository contaRepository, TransferenciaRepository transferenciaRepository,
                          ContaService contaService, Clock clock) {
        this.contaRepository = contaRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.contaService = contaService;
        this.clock = clock;
    }

    /**
     * Estorna uma transferência previamente confirmada.
     *
     * @param transferenciaId ID único da transferência a ser estornada.
     * @return A nova transferência de estorno (estado CONFIRMADA, sentido inverso).
     */
    @Transactional
    public Transferencia estornar(UUID transferenciaId) {
        log.info("[ESTORNO] Iniciando estorno da transferência ID={}", transferenciaId);

        // --- PASSO 1: Busca e bloqueia a transferência original (anti-race-condition de estorno duplo) ---
        // O FOR UPDATE no findByIdForUpdate impede que dois pedidos de estorno
        // simultâneos para a mesma transferência processem ao mesmo tempo.
        Transferencia original = transferenciaRepository.findByIdForUpdate(transferenciaId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "TRANSFERENCIA_INEXISTENTE", "Transferência não encontrada"));

        // --- PASSO 2: Valida estado ---
        original.validarParaEstorno();

        // --- PASSO 3: Verifica se já foi estornada (via query direta no banco) ---
        if (transferenciaRepository.existsEstornoConfirmado(transferenciaId)) {
            log.warn("[ESTORNO RECUSADO] Transferência {} já possui um estorno registrado.", transferenciaId);
            throw new BusinessException(HttpStatus.CONFLICT, "JA_ESTORNADA", "Transferência já foi estornada");
        }

        // --- PASSO 4: Adquire locks em ordem canônica (anti-deadlock) ---
        log.debug("[ESTORNO] Adquirindo locks nas contas de origem e destino");
        List<UUID> orderedIds = Arrays.asList(original.getContaOrigem().getId(), original.getContaDestino().getId());
        orderedIds.sort(UUID::compareTo);
        List<Conta> lockedContas = contaRepository.findByIdInForUpdateOrderById(orderedIds);

        Conta origem = lockedContas.stream()
                .filter(c -> c.getId().equals(original.getContaOrigem().getId())).findFirst().get();
        Conta destino = lockedContas.stream()
                .filter(c -> c.getId().equals(original.getContaDestino().getId())).findFirst().get();

        // --- PASSO 5: Valida saldo do destino (quem precisa devolver) ---
        // Decisão do README nº 1: Recusamos o estorno se o saldo for insuficiente,
        // preservando a regra "saldo de usuário nunca fica negativo".
        if (!destino.isSistema() && destino.getSaldoCentavos() < original.getValorCentavos()) {
            log.warn("[ESTORNO RECUSADO] Conta destino {} não tem saldo. Saldo={}, Necessário={}",
                    destino.getNumero(), destino.getSaldoCentavos(), original.getValorCentavos());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SALDO_DESTINO_INSUFICIENTE", "A conta de destino não tem saldo suficiente para o estorno");
        }

        // --- PASSO 6: Muda estado da original para ESTORNADA ---
        // O valor e os movimentos da original NÃO são tocados (imutabilidade).
        original.setEstado(EstadoTransferencia.ESTORNADA);
        transferenciaRepository.save(original);
        log.debug("[ESTORNO] Transferência original {} marcada como ESTORNADA", transferenciaId);

        // --- PASSO 7: Cria a transferência de estorno (sentido inverso) e gera movimentos ---
        Transferencia estorno = new Transferencia();
        estorno.setContaOrigem(destino);   // O destino original agora é a origem
        estorno.setContaDestino(origem);   // A origem original agora é o destino
        estorno.setValorCentavos(original.getValorCentavos());
        estorno.setTaxaCentavos(0L);       // Estorno não cobra taxa
        estorno.setEstado(EstadoTransferencia.CONFIRMADA);
        estorno.setTransferenciaOriginal(original); // Referência à original
        estorno.setConcluidaEm(ZonedDateTime.now(clock));
        estorno = transferenciaRepository.save(estorno);

        // Gera os movimentos da devolução do valor principal
        contaService.debitar(destino, original.getValorCentavos(), estorno);
        contaService.creditar(origem, original.getValorCentavos(), estorno);

        // --- PASSO 8: Devolve a taxa para a conta de origem (se havia taxa) ---
        if (original.getTaxaCentavos() > 0) {
            log.debug("[ESTORNO] Devolvendo taxa de {} centavos da SISTEMA-TAXAS para {}", original.getTaxaCentavos(), origem.getNumero());
            Conta sistemaTaxas = contaRepository.findByNumeroForUpdate("SISTEMA-TAXAS").get();
            contaService.debitar(sistemaTaxas, original.getTaxaCentavos(), estorno);
            contaService.creditar(origem, original.getTaxaCentavos(), estorno);
        }

        log.info("[ESTORNO CONCLUIDO] Estorno ID={} para transferência original ID={} | Valor devolvido={} centavos",
                estorno.getId(), transferenciaId, original.getValorCentavos());
        return estorno;
    }
}
