package com.deltaglobal.testebackend.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public class EstornoRequest {
    @Schema(example = "Transferência enviada por engano")
    @NotBlank
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
