package com.deltaglobal.testebackend.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "conta")
public class Conta {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "saldo_centavos", nullable = false)
    private Long saldoCentavos;

    @Column(name = "limite_diario_centavos", nullable = false)
    private Long limiteDiarioCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConta estado;

    @Version
    @Column(nullable = false)
    private Long versao;

    public boolean isSistema() {
        return usuarioId == null;
    }

    public boolean isBloqueada() {
        return estado == EstadoConta.BLOQUEADA;
    }

    public boolean isEncerrada() {
        return estado == EstadoConta.ENCERRADA;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public Long getSaldoCentavos() { return saldoCentavos; }
    public void setSaldoCentavos(Long saldoCentavos) { this.saldoCentavos = saldoCentavos; }
    public Long getLimiteDiarioCentavos() { return limiteDiarioCentavos; }
    public void setLimiteDiarioCentavos(Long limiteDiarioCentavos) { this.limiteDiarioCentavos = limiteDiarioCentavos; }
    public EstadoConta getEstado() { return estado; }
    public void setEstado(EstadoConta estado) { this.estado = estado; }
    public Long getVersao() { return versao; }
    public void setVersao(Long versao) { this.versao = versao; }
}
