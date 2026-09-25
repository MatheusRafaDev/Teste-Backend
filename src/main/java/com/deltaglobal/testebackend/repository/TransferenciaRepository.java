package com.deltaglobal.testebackend.repository;

import com.deltaglobal.testebackend.domain.EstadoTransferencia;
import com.deltaglobal.testebackend.domain.Transferencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferenciaRepository extends JpaRepository<Transferencia, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transferencia t WHERE t.id = :id")
    Optional<Transferencia> findByIdForUpdate(UUID id);

    /**
     * Soma os valores transferidos por uma conta de origem no dia atual (janela de tempo).
     * Exclui estornos (transferenciaOriginal IS NULL) e considera apenas CONFIRMADAS.
     * Executado como agregação no banco — não traz dados para a JVM.
     */
    @Query("""
            SELECT COALESCE(SUM(t.valorCentavos), 0)
            FROM Transferencia t
            WHERE t.contaOrigem.id = :contaOrigemId
              AND t.estado = :estado
              AND t.transferenciaOriginal IS NULL
              AND t.criadaEm >= :inicio
              AND t.criadaEm <= :fim
            """)
    Long sumValorTransferidoNoDia(UUID contaOrigemId,
                                  EstadoTransferencia estado,
                                  ZonedDateTime inicio,
                                  ZonedDateTime fim);

    /**
     * Verifica se já existe um estorno CONFIRMADO para uma transferência original.
     * Usado para impedir duplo estorno sem fazer findAll() em memória.
     */
    @Query("""
            SELECT COUNT(t) > 0
            FROM Transferencia t
            WHERE t.transferenciaOriginal.id = :transferenciaOriginalId
              AND t.estado = com.deltaglobal.testebackend.domain.EstadoTransferencia.CONFIRMADA
            """)
    boolean existsEstornoConfirmado(UUID transferenciaOriginalId);
}
