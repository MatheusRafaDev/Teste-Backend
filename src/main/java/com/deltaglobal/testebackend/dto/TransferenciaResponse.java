package com.deltaglobal.testebackend.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class TransferenciaResponse {
    private UUID transferenciaId;
    private String contaOrigem;
    private String contaDestino;
    private BigDecimal valor;
    private BigDecimal taxa;
    private BigDecimal totalDebitado;
    private BigDecimal saldoOrigemApos;
    private ZonedDateTime concluidaEm;

    // Getters and Setters
    public UUID getTransferenciaId() { return transferenciaId; }
    public void setTransferenciaId(UUID transferenciaId) { this.transferenciaId = transferenciaId; }
    public String getContaOrigem() { return contaOrigem; }
    public void setContaOrigem(String contaOrigem) { this.contaOrigem = contaOrigem; }
    public String getContaDestino() { return contaDestino; }
    public void setContaDestino(String contaDestino) { this.contaDestino = contaDestino; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public BigDecimal getTaxa() { return taxa; }
    public void setTaxa(BigDecimal taxa) { this.taxa = taxa; }
    public BigDecimal getTotalDebitado() { return totalDebitado; }
    public void setTotalDebitado(BigDecimal totalDebitado) { this.totalDebitado = totalDebitado; }
    public BigDecimal getSaldoOrigemApos() { return saldoOrigemApos; }
    public void setSaldoOrigemApos(BigDecimal saldoOrigemApos) { this.saldoOrigemApos = saldoOrigemApos; }
    public ZonedDateTime getConcluidaEm() { return concluidaEm; }
    public void setConcluidaEm(ZonedDateTime concluidaEm) { this.concluidaEm = concluidaEm; }
}
