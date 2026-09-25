package com.deltaglobal.testebackend.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "transferencia")
public class Transferencia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_origem_id")
    private Conta contaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_destino_id")
    private Conta contaDestino;

    @Column(name = "valor_centavos", nullable = false)
    private Long valorCentavos;

    @Column(name = "taxa_centavos", nullable = false)
    private Long taxaCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransferencia estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_original_id")
    private Transferencia transferenciaOriginal;

    @Column(name = "criada_em", nullable = false)
    private ZonedDateTime criadaEm;

    @Column(name = "concluida_em")
    private ZonedDateTime concluidaEm;

    @PrePersist
    protected void onCreate() {
        if (criadaEm == null) criadaEm = ZonedDateTime.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Conta getContaOrigem() { return contaOrigem; }
    public void setContaOrigem(Conta contaOrigem) { this.contaOrigem = contaOrigem; }
    public Conta getContaDestino() { return contaDestino; }
    public void setContaDestino(Conta contaDestino) { this.contaDestino = contaDestino; }
    public Long getValorCentavos() { return valorCentavos; }
    public void setValorCentavos(Long valorCentavos) { this.valorCentavos = valorCentavos; }
    public Long getTaxaCentavos() { return taxaCentavos; }
    public void setTaxaCentavos(Long taxaCentavos) { this.taxaCentavos = taxaCentavos; }
    public EstadoTransferencia getEstado() { return estado; }
    public void setEstado(EstadoTransferencia estado) { this.estado = estado; }
    public Transferencia getTransferenciaOriginal() { return transferenciaOriginal; }
    public void setTransferenciaOriginal(Transferencia transferenciaOriginal) { this.transferenciaOriginal = transferenciaOriginal; }
    public ZonedDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(ZonedDateTime criadaEm) { this.criadaEm = criadaEm; }
    public ZonedDateTime getConcluidaEm() { return concluidaEm; }
    public void setConcluidaEm(ZonedDateTime concluidaEm) { this.concluidaEm = concluidaEm; }
}
