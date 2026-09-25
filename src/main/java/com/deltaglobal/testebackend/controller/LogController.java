package com.deltaglobal.testebackend.controller;

import com.deltaglobal.testebackend.domain.LogAuditoria;
import com.deltaglobal.testebackend.repository.LogAuditoriaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.context.annotation.Profile("dev")
public class LogController {

    private final LogAuditoriaRepository logAuditoriaRepository;

    public LogController(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    @GetMapping("/logs")
    public Page<LogAuditoria> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return logAuditoriaRepository.findAllByOrderByCriadoEmDesc(PageRequest.of(page, size));
    }
}
