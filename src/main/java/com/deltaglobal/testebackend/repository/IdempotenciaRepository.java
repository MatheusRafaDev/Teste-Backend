package com.deltaglobal.testebackend.repository;

import com.deltaglobal.testebackend.domain.Idempotencia;
import com.deltaglobal.testebackend.domain.IdempotenciaId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotenciaRepository extends JpaRepository<Idempotencia, IdempotenciaId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Idempotencia i WHERE i.id = :id")
    Optional<Idempotencia> findByIdForUpdate(IdempotenciaId id);
}
