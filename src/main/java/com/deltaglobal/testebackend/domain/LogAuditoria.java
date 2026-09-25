package com.deltaglobal.testebackend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "log_auditoria")
public class LogAuditoria {

    @Id
    private UUID id = UUID.randomUUID();

    private String metodo;
    private String endpoint;
    
    @Column(name = "status_http")
    private Integer statusHttp;
    
    @Column(name = "payload_requisicao")
    private String payloadRequisicao;
    
    @Column(name = "payload_resposta")
    private String payloadResposta;
    
    @Column(name = "tempo_execucao_ms")
    private Long tempoExecucaoMs;
    
    @Column(name = "criado_em")
    private ZonedDateTime criadoEm = ZonedDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public Integer getStatusHttp() { return statusHttp; }
    public void setStatusHttp(Integer statusHttp) { this.statusHttp = statusHttp; }
    public String getPayloadRequisicao() { return payloadRequisicao; }
    public void setPayloadRequisicao(String payloadRequisicao) { this.payloadRequisicao = payloadRequisicao; }
    public String getPayloadResposta() { return payloadResposta; }
    public void setPayloadResposta(String payloadResposta) { this.payloadResposta = payloadResposta; }
    public Long getTempoExecucaoMs() { return tempoExecucaoMs; }
    public void setTempoExecucaoMs(Long tempoExecucaoMs) { this.tempoExecucaoMs = tempoExecucaoMs; }
    public ZonedDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(ZonedDateTime criadoEm) { this.criadoEm = criadoEm; }
}
