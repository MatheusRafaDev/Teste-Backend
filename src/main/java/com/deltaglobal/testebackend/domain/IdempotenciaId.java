package com.deltaglobal.testebackend.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotenciaId implements Serializable {
    private String chave;
    private String endpoint;

    public IdempotenciaId() {}

    public IdempotenciaId(String chave, String endpoint) {
        this.chave = chave;
        this.endpoint = endpoint;
    }

    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotenciaId that = (IdempotenciaId) o;
        return Objects.equals(chave, that.chave) && Objects.equals(endpoint, that.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chave, endpoint);
    }
}
