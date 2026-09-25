package com.deltaglobal.testebackend.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class TaxaServiceTest {

    private final TaxaService taxaService = new TaxaService();

    @Test
    void testCalcularTaxa() {
        assertEquals(0L, taxaService.calcularTaxa(1000L)); // 10,00 -> 0
        assertEquals(0L, taxaService.calcularTaxa(10000L)); // 100,00 -> 0
        assertEquals(100L, taxaService.calcularTaxa(10001L)); // 100,01 -> 1% is ~1, max is min(1,100) -> 1,00
        assertEquals(113L, taxaService.calcularTaxa(11250L)); // 112,50 -> 1,125 rounded to 1,13
        assertEquals(150L, taxaService.calcularTaxa(15000L)); // 150,00 -> 1,50
        assertEquals(1235L, taxaService.calcularTaxa(123456L)); // 1234,56 -> 12,3456 rounded to 12,35
        assertEquals(2000L, taxaService.calcularTaxa(200000L)); // 2000,00 -> 20,00
        assertEquals(2000L, taxaService.calcularTaxa(500000L)); // 5000,00 -> 20,00 (teto)
    }
}
