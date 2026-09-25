package com.deltaglobal.testebackend.repository;

import com.deltaglobal.testebackend.domain.LogAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID> {
    Page<LogAuditoria> findAllByOrderByCriadoEmDesc(Pageable pageable);
}
