package com.deltaglobal.testebackend.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public class DepositoRequest {
    @Schema(example = "50.00")
    @NotNull
    private BigDecimal valor;

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
