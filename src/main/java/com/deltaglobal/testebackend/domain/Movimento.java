package com.deltaglobal.testebackend.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimento")
public class Movimento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_id")
    private Transferencia transferencia;

    @Column(nullable = false)
    private Long sequencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimento tipo;

    @Column(name = "valor_centavos", nullable = false)
    private Long valorCentavos;

    @Column(name = "saldo_apos_centavos", nullable = false)
    private Long saldoAposCentavos;

    @Column(name = "criado_em", nullable = false)
    private ZonedDateTime criadoEm;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @PrePersist
    protected void onCreate() {
        if (criadoEm == null) criadoEm = ZonedDateTime.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Conta getConta() { return conta; }
    public void setConta(Conta conta) { this.conta = conta; }
    public Transferencia getTransferencia() { return transferencia; }
    public void setTransferencia(Transferencia transferencia) { this.transferencia = transferencia; }
    public Long getSequencia() { return sequencia; }
    public void setSequencia(Long sequencia) { this.sequencia = sequencia; }
    public TipoMovimento getTipo() { return tipo; }
    public void setTipo(TipoMovimento tipo) { this.tipo = tipo; }
    public Long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(Long valorCentavos) { this.valorCentavos = valorCentavos; }
    public Long getSaldoAposCentavos() { return saldoAposCentavos; }
    public void setSaldoAposCentavos(Long saldoAposCentavos) { this.saldoAposCentavos = saldoAposCentavos; }
    public ZonedDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(ZonedDateTime criadoEm) { this.criadoEm = criadoEm; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
