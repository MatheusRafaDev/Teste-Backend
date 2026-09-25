package com.deltaglobal.testebackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public class TransferenciaRequest {
    @Schema(example = "CONTA-001")
    @NotBlank
    private String contaOrigem;
    @Schema(example = "CONTA-002")
    @NotBlank
    private String contaDestino;
    @Schema(example = "100.00")
    @NotNull
    @Digits(integer = 13, fraction = 2)
    private BigDecimal valor;

    public String getContaOrigem() { return contaOrigem; }
    public void setContaOrigem(String contaOrigem) { this.contaOrigem = contaOrigem; }
    public String getContaDestino() { return contaDestino; }
    public void setContaDestino(String contaDestino) { this.contaDestino = contaDestino; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
