package com.deltaglobal.testebackend.repository;

import com.deltaglobal.testebackend.domain.Agendamento;
import com.deltaglobal.testebackend.domain.EstadoAgendamento;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT a FROM Agendamento a WHERE a.estado = :estado AND a.executarEm <= :now ORDER BY a.executarEm ASC")
    List<Agendamento> findForProcessing(EstadoAgendamento estado, ZonedDateTime now, Pageable pageable);
}
