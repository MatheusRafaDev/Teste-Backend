package com.deltaglobal.testebackend.repository;

import java.math.BigDecimal;

public class ResumoDto {
    private int contasAtivasAgora;
    private BigDecimal saldoTotalUsuariosAgora;
    private int agendamentosPendentesAgora;
    private int transferenciasNaJanela;
    private BigDecimal valorTransferidoNaJanela;
    private BigDecimal taxasNaJanela;
    private BigDecimal ticketMedioNaJanela;
    private int estornosNaJanela;
    private boolean somaDosMovimentosEhZero;

    // Getters and setters
    public int getContasAtivasAgora() { return contasAtivasAgora; }
    public void setContasAtivasAgora(int contasAtivasAgora) { this.contasAtivasAgora = contasAtivasAgora; }
    public BigDecimal getSaldoTotalUsuariosAgora() { return saldoTotalUsuariosAgora; }
    public void setSaldoTotalUsuariosAgora(BigDecimal saldoTotalUsuariosAgora) { this.saldoTotalUsuariosAgora = saldoTotalUsuariosAgora; }
    public int getAgendamentosPendentesAgora() { return agendamentosPendentesAgora; }
    public void setAgendamentosPendentesAgora(int agendamentosPendentesAgora) { this.agendamentosPendentesAgora = agendamentosPendentesAgora; }
    public int getTransferenciasNaJanela() { return transferenciasNaJanela; }
    public void setTransferenciasNaJanela(int transferenciasNaJanela) { this.transferenciasNaJanela = transferenciasNaJanela; }
    public BigDecimal getValorTransferidoNaJanela() { return valorTransferidoNaJanela; }
    public void setValorTransferidoNaJanela(BigDecimal valorTransferidoNaJanela) { this.valorTransferidoNaJanela = valorTransferidoNaJanela; }
    public BigDecimal getTaxasNaJanela() { return taxasNaJanela; }
    public void setTaxasNaJanela(BigDecimal taxasNaJanela) { this.taxasNaJanela = taxasNaJanela; }
    public BigDecimal getTicketMedioNaJanela() { return ticketMedioNaJanela; }
    public void setTicketMedioNaJanela(BigDecimal ticketMedioNaJanela) { this.ticketMedioNaJanela = ticketMedioNaJanela; }
    public int getEstornosNaJanela() { return estornosNaJanela; }
    public void setEstornosNaJanela(int estornosNaJanela) { this.estornosNaJanela = estornosNaJanela; }
    public boolean isSomaDosMovimentosEhZero() { return somaDosMovimentosEhZero; }
    public void setSomaDosMovimentosEhZero(boolean somaDosMovimentosEhZero) { this.somaDosMovimentosEhZero = somaDosMovimentosEhZero; }
}
