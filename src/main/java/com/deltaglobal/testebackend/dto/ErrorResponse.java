package com.deltaglobal.testebackend.dto;

import java.time.ZonedDateTime;

public class ErrorResponse {
    private ZonedDateTime timestamp;
    private int status;
    private String error;
    private String codigo;
    private String message;
    private String path;

    public ErrorResponse() {}

    public ErrorResponse(int status, String error, String codigo, String message, String path) {
        this.timestamp = ZonedDateTime.now();
        this.status = status;
        this.error = error;
        this.codigo = codigo;
        this.message = message;
        this.path = path;
    }

    // Getters and Setters
    public ZonedDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(ZonedDateTime timestamp) { this.timestamp = timestamp; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
