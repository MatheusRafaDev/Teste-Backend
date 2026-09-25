package com.deltaglobal.testebackend.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String codigo;

    public BusinessException(HttpStatus httpStatus, String codigo, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.codigo = codigo;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCodigo() { return codigo; }
}
