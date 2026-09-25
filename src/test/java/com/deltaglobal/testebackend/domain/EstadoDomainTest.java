package com.deltaglobal.testebackend.domain;

import com.deltaglobal.testebackend.exception.BusinessException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class EstadoDomainTest {

    @Test
    void contaBloqueadaNaoPodeEnviarMasPodeReceber() {
        Conta conta = contaComEstado(EstadoConta.BLOQUEADA);

        BusinessException exception = assertThrows(BusinessException.class, conta::validarAtivaParaSaida);

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        assertEquals("CONTA_ORIGEM_INVALIDA", exception.getCodigo());
        assertDoesNotThrow(conta::validarAtivaParaEntrada);
    }

    @Test
    void contaEncerradaNaoPodeEnviarNemReceber() {
        Conta conta = contaComEstado(EstadoConta.ENCERRADA);

        assertEquals("CONTA_ORIGEM_INVALIDA",
                assertThrows(BusinessException.class, conta::validarAtivaParaSaida).getCodigo());
        assertEquals("CONTA_DESTINO_INVALIDA",
                assertThrows(BusinessException.class, conta::validarAtivaParaEntrada).getCodigo());
    }

    @Test
    void somenteTransferenciaConfirmadaPodeSerEstornada() {
        Transferencia criada = transferenciaComEstado(EstadoTransferencia.CRIADA);
        Transferencia falhada = transferenciaComEstado(EstadoTransferencia.FALHADA);
        Transferencia confirmada = transferenciaComEstado(EstadoTransferencia.CONFIRMADA);
        Transferencia estornada = transferenciaComEstado(EstadoTransferencia.ESTORNADA);

        assertEquals("ESTADO_INVALIDO",
                assertThrows(BusinessException.class, criada::validarParaEstorno).getCodigo());
        assertEquals("ESTADO_INVALIDO",
                assertThrows(BusinessException.class, falhada::validarParaEstorno).getCodigo());
        assertDoesNotThrow(confirmada::validarParaEstorno);
        assertEquals("JA_ESTORNADA",
                assertThrows(BusinessException.class, estornada::validarParaEstorno).getCodigo());
    }

    private Conta contaComEstado(EstadoConta estado) {
        Conta conta = new Conta();
        conta.setEstado(estado);
        return conta;
    }

    private Transferencia transferenciaComEstado(EstadoTransferencia estado) {
        Transferencia transferencia = new Transferencia();
        transferencia.setEstado(estado);
        return transferencia;
    }
}
