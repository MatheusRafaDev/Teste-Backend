package com.deltaglobal.testebackend.service;

import com.deltaglobal.testebackend.domain.Idempotencia;
import com.deltaglobal.testebackend.domain.IdempotenciaId;
import com.deltaglobal.testebackend.exception.BusinessException;
import com.deltaglobal.testebackend.repository.IdempotenciaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class IdempotenciaService {

    private final IdempotenciaRepository idempotenciaRepository;
    private final ObjectMapper objectMapper;

    public IdempotenciaService(IdempotenciaRepository idempotenciaRepository, ObjectMapper objectMapper) {
        this.idempotenciaRepository = idempotenciaRepository;
        this.objectMapper = objectMapper;
    }

    public String hashRequest(Object requestBody) {
        try {
            String json = requestBody != null ? objectMapper.writeValueAsString(requestBody) : "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao gerar hash", e);
        }
    }

    // Usado dentro da transacao principal para salvar. Se der UniqueConstraintViolation, a transacao morre e o controller trata
    public void registrarIdempotencia(String chave, String endpoint, String hash, Object responseBody, int status) {
        try {
            Idempotencia i = new Idempotencia();
            i.setId(new IdempotenciaId(chave, endpoint));
            i.setHashRequisicao(hash);
            i.setRespostaJson(objectMapper.writeValueAsString(responseBody));
            i.setStatusHttp(status);
            idempotenciaRepository.saveAndFlush(i);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar resposta", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Idempotencia buscarIdempotenciaExistente(String chave, String endpoint) {
        return idempotenciaRepository.findById(new IdempotenciaId(chave, endpoint)).orElse(null);
    }
}
