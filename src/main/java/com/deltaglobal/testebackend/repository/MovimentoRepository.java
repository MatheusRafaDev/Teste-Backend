package com.deltaglobal.testebackend.repository;

import com.deltaglobal.testebackend.domain.Movimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.UUID;

@Repository
public interface MovimentoRepository extends JpaRepository<Movimento, UUID> {
    
    @Query("SELECT COUNT(m) FROM Movimento m WHERE m.conta.id = :contaId")
    Long countByContaId(UUID contaId);

    Movimento findTopByContaIdOrderBySequenciaDesc(UUID contaId);

    Page<Movimento> findByContaId(UUID contaId, Pageable pageable);

    Page<Movimento> findByContaIdAndCriadoEmBetween(UUID contaId, ZonedDateTime de, ZonedDateTime ate, Pageable pageable);

    Page<Movimento> findByContaIdAndCriadoEmGreaterThanEqual(UUID contaId, ZonedDateTime de, Pageable pageable);

    Page<Movimento> findByContaIdAndCriadoEmLessThanEqual(UUID contaId, ZonedDateTime ate, Pageable pageable);
    
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valorCentavos ELSE -m.valorCentavos END), 0) FROM Movimento m")
    Long sumAllMovimentos();
}
