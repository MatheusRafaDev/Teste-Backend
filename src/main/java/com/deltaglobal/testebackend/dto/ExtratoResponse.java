package com.deltaglobal.testebackend.dto;

import java.math.BigDecimal;
import java.util.List;

public class ExtratoResponse {
    private BigDecimal saldoAtual;
    private List<MovimentoDto> movimentos;

    public BigDecimal getSaldoAtual() { return saldoAtual; }
    public void setSaldoAtual(BigDecimal saldoAtual) { this.saldoAtual = saldoAtual; }
    public List<MovimentoDto> getMovimentos() { return movimentos; }
    public void setMovimentos(List<MovimentoDto> movimentos) { this.movimentos = movimentos; }
}
