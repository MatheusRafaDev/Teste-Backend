package com.deltaglobal.testebackend.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "idempotencia")
public class Idempotencia {

    @EmbeddedId
    private IdempotenciaId id;

    @Column(name = "hash_requisicao", nullable = false)
    private String hashRequisicao;

    @Column(name = "resposta_json")
    private String respostaJson;

    @Column(name = "status_http")
    private Integer statusHttp;

    @Column(name = "criado_em", nullable = false)
    private ZonedDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) criadoEm = ZonedDateTime.now();
    }

    // Getters and setters
    public IdempotenciaId getId() { return id; }
    public void setId(IdempotenciaId id) { this.id = id; }
    public String getHashRequisicao() { return hashRequisicao; }
    public void setHashRequisicao(String hashRequisicao) { this.hashRequisicao = hashRequisicao; }
    public String getRespostaJson() { return respostaJson; }
    public void setRespostaJson(String respostaJson) { this.respostaJson = respostaJson; }
    public Integer getStatusHttp() { return statusHttp; }
    public void setStatusHttp(Integer statusHttp) { this.statusHttp = statusHttp; }
    public ZonedDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(ZonedDateTime criadoEm) { this.criadoEm = criadoEm; }
}
