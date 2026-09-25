package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.*;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.ContaRepository;
import com.deltaglobal.testebackend.repository.MovimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço responsável pelas operações atômicas de débito e crédito em contas.
 *
 * REGRA IMUTÁVEL: Nenhuma operação de saldo pode ocorrer sem que um
 * Movimento correspondente seja gravado na mesma transação. Isso garante
 * o Extrato Imutável e a consistência de saldo definida pelo desafio.
 *
 * A sequência de cada movimento é calculada via COUNT(*) na tabela `movimento`
 * dentro da transação, garantindo que, mesmo com concorrência, dois movimentos
 * não recebam o mesmo número de sequência para a mesma conta.
 */
@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);

    private final ContaRepository contaRepository;
    private final MovimentoRepository movimentoRepository;

    public ContaService(ContaRepository contaRepository, MovimentoRepository movimentoRepository) {
        this.contaRepository = contaRepository;
        this.movimentoRepository = movimentoRepository;
    }

    /**
     * Credita um valor na conta e registra o movimento de ENTRADA correspondente.
     *
     * @param conta         A entidade da conta a ser creditada (deve estar em estado LOCKED).
     * @param valor         Valor em centavos (deve ser positivo).
     * @param transferencia A transferência que originou este movimento.
     */
    @Transactional
    public void creditar(Conta conta, Long valor, Transferencia transferencia) {
        long saldoAntes = conta.getSaldoCentavos();
        conta.setSaldoCentavos(saldoAntes + valor);
        contaRepository.save(conta);

        Movimento ultimo = movimentoRepository.findTopByContaIdOrderBySequenciaDesc(conta.getId());
        Long sequencia = ultimo == null ? 1L : ultimo.getSequencia() + 1;
        String hashAnterior = ultimo == null ? "0000000000000000000000000000000000000000000000000000000000000000" : ultimo.getHash();

        Movimento m = new Movimento();
        m.setConta(conta);
        m.setTransferencia(transferencia);
        m.setSequencia(sequencia);
        m.setTipo(TipoMovimento.ENTRADA);
        m.setValorCentavos(valor);
        m.setSaldoAposCentavos(conta.getSaldoCentavos());
        m.setHash(gerarHash(conta.getId().toString(), sequencia, hashAnterior, TipoMovimento.ENTRADA, conta.getSaldoCentavos()));
        movimentoRepository.save(m);

        log.info("[CREDITO] Conta={} | Antes={} centavos | +{} centavos | Após={} centavos | Seq={} | Hash={}",
                conta.getNumero(), saldoAntes, valor, conta.getSaldoCentavos(), sequencia, m.getHash().substring(0,8));
    }

    /**
     * Debita um valor da conta e registra o movimento de SAIDA correspondente.
     * Lança exceção se a conta não for do sistema e o saldo for insuficiente.
     *
     * @param conta         A entidade da conta a ser debitada (deve estar em estado LOCKED).
     * @param valor         Valor em centavos (deve ser positivo).
     * @param transferencia A transferência que originou este movimento.
     */
    @Transactional
    public void debitar(Conta conta, Long valor, Transferencia transferencia) {
        // Contas do sistema (SISTEMA-ENTRADA, SISTEMA-TAXAS) podem ter saldo negativo
        if (!conta.isSistema() && conta.getSaldoCentavos() < valor) {
            log.warn("[DEBITO NEGADO] Conta={} | Saldo={} centavos | Tentativa de débito={} centavos",
                    conta.getNumero(), conta.getSaldoCentavos(), valor);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SALDO_INSUFICIENTE", "Saldo insuficiente");
        }

        long saldoAntes = conta.getSaldoCentavos();
        conta.setSaldoCentavos(saldoAntes - valor);
        contaRepository.save(conta);

        Movimento ultimo = movimentoRepository.findTopByContaIdOrderBySequenciaDesc(conta.getId());
        Long sequencia = ultimo == null ? 1L : ultimo.getSequencia() + 1;
        String hashAnterior = ultimo == null ? "0000000000000000000000000000000000000000000000000000000000000000" : ultimo.getHash();

        Movimento m = new Movimento();
        m.setConta(conta);
        m.setTransferencia(transferencia);
        m.setSequencia(sequencia);
        m.setTipo(TipoMovimento.SAIDA);
        m.setValorCentavos(valor);
        m.setSaldoAposCentavos(conta.getSaldoCentavos());
        m.setHash(gerarHash(conta.getId().toString(), sequencia, hashAnterior, TipoMovimento.SAIDA, conta.getSaldoCentavos()));
        movimentoRepository.save(m);

        log.info("[DEBITO] Conta={} | Antes={} centavos | -{} centavos | Após={} centavos | Seq={} | Hash={}",
                conta.getNumero(), saldoAntes, valor, conta.getSaldoCentavos(), sequencia, m.getHash().substring(0,8));
    }

    /**
     * Busca uma conta aplicando um lock pessimista (SELECT FOR UPDATE).
     * Usado quando a conta é usada individualmente (ex: depósito na SISTEMA-ENTRADA).
     *
     * @param numero Número identificador da conta (ex: "CONTA-001").
     * @return A conta com lock adquirido.
     */
    public Conta getContaForUpdate(String numero) {
        return contaRepository.findByNumeroForUpdate(numero)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONTA_INEXISTENTE", "Conta inexistente"));
    }

    private String gerarHash(String idConta, Long seq, String hashAnterior, TipoMovimento tipo, Long valorApos) {
        try {
            String originalString = idConta + seq + hashAnterior + tipo.name() + valorApos;
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(originalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
