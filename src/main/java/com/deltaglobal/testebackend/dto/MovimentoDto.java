package com.deltaglobal.testebackend.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class MovimentoDto {
    private UUID movimentoId;
    private String tipo;
    private BigDecimal valor;
    private BigDecimal saldoApos;
    private ZonedDateTime data;
    private UUID transferenciaId;

    public UUID getMovimentoId() { return movimentoId; }
    public void setMovimentoId(UUID movimentoId) { this.movimentoId = movimentoId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public BigDecimal getSaldoApos() { return saldoApos; }
    public void setSaldoApos(BigDecimal saldoApos) { this.saldoApos = saldoApos; }
    public ZonedDateTime getData() { return data; }
    public void setData(ZonedDateTime data) { this.data = data; }
    public UUID getTransferenciaId() { return transferenciaId; }
    public void setTransferenciaId(UUID transferenciaId) { this.transferenciaId = transferenciaId; }
}
