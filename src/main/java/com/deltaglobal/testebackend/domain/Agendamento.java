package com.deltaglobal.testebackend.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "agendamento")
public class Agendamento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_origem_id", nullable = false)
    private Conta contaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_destino_id", nullable = false)
    private Conta contaDestino;

    @Column(name = "valor_centavos", nullable = false)
    private Long valorCentavos;

    @Column(name = "executar_em", nullable = false)
    private ZonedDateTime executarEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAgendamento estado;

    @Column(nullable = false)
    private Integer tentativas = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_id")
    private Transferencia transferencia;

    @Column(name = "atualizado_em", nullable = false)
    private ZonedDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        this.atualizadoEm = ZonedDateTime.now();
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
    public ZonedDateTime getExecutarEm() { return executarEm; }
    public void setExecutarEm(ZonedDateTime executarEm) { this.executarEm = executarEm; }
    public EstadoAgendamento getEstado() { return estado; }
    public void setEstado(EstadoAgendamento estado) { this.estado = estado; }
    public Integer getTentativas() { return tentativas; }
    public void setTentativas(Integer tentativas) { this.tentativas = tentativas; }
    public Transferencia getTransferencia() { return transferencia; }
    public void setTransferencia(Transferencia transferencia) { this.transferencia = transferencia; }
    public ZonedDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(ZonedDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
