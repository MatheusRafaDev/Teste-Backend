package com.deltaglobal.testebackend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    private UUID id;
    private String nome;
    private String cpf;

    /**
     * Retorna o CPF mascarado no formato ***.456.789-** conforme exigido pelo README.
     * O CPF completo NUNCA deve aparecer na resposta da API nem em logs.
     */
    public String getCpfMascarado() {
        if (cpf == null || cpf.length() < 11) return "***.***.***-**";
        // CPF armazenado como 11 dígitos sem formatação: ex. "12345678901"
        String digits = cpf.replaceAll("[^0-9]", "");
        if (digits.length() != 11) return "***.***.***-**";
        // Formato: ***.DDD.DDD-**  (oculta os 3 primeiros e os 2 últimos)
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
}

