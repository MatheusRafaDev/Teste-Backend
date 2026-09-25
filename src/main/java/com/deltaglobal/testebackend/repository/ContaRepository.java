package com.deltaglobal.testebackend.repository;

import com.deltaglobal.testebackend.domain.Conta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaRepository extends JpaRepository<Conta, UUID> {
    Optional<Conta> findByNumero(String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conta c WHERE c.numero = :numero")
    Optional<Conta> findByNumeroForUpdate(String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conta c WHERE c.id IN :ids ORDER BY c.id ASC")
    List<Conta> findByIdInForUpdateOrderById(List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conta c WHERE c.numero IN :numeros ORDER BY c.numero ASC")
    List<Conta> findByNumeroInForUpdateOrderByNumero(List<String> numeros);
}
