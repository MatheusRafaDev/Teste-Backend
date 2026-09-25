package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Conta;
import com.deltaglobal.testebackend.domain.EstadoTransferencia;
import com.deltaglobal.testebackend.domain.Transferencia;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.ContaRepository;
import com.deltaglobal.testebackend.repository.TransferenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * Serviço responsável por efetuar depósitos em contas de usuários.
 *
 * Um depósito é modelado como uma transferência da conta de sistema
 * "SISTEMA-ENTRADA" para a conta do usuário. Isso garante a regra de
 * "partidas dobradas": toda entrada tem uma saída correspondente, e a
 * soma de todos os movimentos do banco sempre é zero.
 *
 * REGRAS:
 * - Depósitos em contas BLOQUEADAS são aceitos (apenas saídas são bloqueadas).
 * - Depósitos em contas ENCERRADAS são recusados com 409.
 * - Depósitos são isentos de taxa.
 */
@Service
public class DepositoService {

    private static final Logger log = LoggerFactory.getLogger(DepositoService.class);

    private final ContaRepository contaRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final ContaService contaService;

    public DepositoService(ContaRepository contaRepository, TransferenciaRepository transferenciaRepository,
                           ContaService contaService) {
        this.contaRepository = contaRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.contaService = contaService;
    }

    /**
     * Realiza o depósito de um valor em uma conta.
     *
     * @param numeroConta   Número da conta de destino do depósito.
     * @param valorCentavos Valor do depósito em centavos (deve ser > 0).
     * @return A Transferência criada representando o depósito.
     */
    @Transactional
    public Transferencia depositar(String numeroConta, Long valorCentavos) {
        log.info("[DEPOSITO] Iniciando depósito de {} centavos na conta {}", valorCentavos, numeroConta);

        if (valorCentavos <= 0 || valorCentavos > 1000000L) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_INVALIDO", "Valor menor ou igual a zero, ou maior que R$ 10.000,00");
        }

        // Valida existência e estado da conta (sem lock ainda, apenas para verificação rápida)
        Conta destino = contaRepository.findByNumero(numeroConta)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONTA_INEXISTENTE", "Conta inexistente"));

        // Contas encerradas não aceitam depósito
        destino.validarAtivaParaEntrada();

        // Adquire locks pessimistas em ambas as contas (SISTEMA-ENTRADA e conta de destino)
        // para garantir que os saldos e movimentos sejam consistentes sob concorrência
        log.debug("[DEPOSITO] Adquirindo locks pessimistas em SISTEMA-ENTRADA e {}", numeroConta);
        Conta sistema = contaRepository.findByNumeroForUpdate("SISTEMA-ENTRADA").get();
        Conta destinoLock = contaRepository.findByNumeroForUpdate(numeroConta).get();

        // Cria o registro de transferência (de SISTEMA-ENTRADA para a conta do usuário)
        Transferencia t = new Transferencia();
        t.setContaOrigem(sistema);
        t.setContaDestino(destinoLock);
        t.setValorCentavos(valorCentavos);
        t.setTaxaCentavos(0L); // Depósitos são isentos de taxa
        t.setEstado(EstadoTransferencia.CONFIRMADA);
        t.setConcluidaEm(ZonedDateTime.now());
        t = transferenciaRepository.save(t);

        // Gera os movimentos: SAIDA em SISTEMA-ENTRADA e ENTRADA na conta do usuário
        contaService.debitar(sistema, valorCentavos, t);
        contaService.creditar(destinoLock, valorCentavos, t);

        log.info("[DEPOSITO CONCLUIDO] Transferência ID={} | SISTEMA-ENTRADA → {} | {} centavos",
                t.getId(), numeroConta, valorCentavos);
        return t;
    }
}
