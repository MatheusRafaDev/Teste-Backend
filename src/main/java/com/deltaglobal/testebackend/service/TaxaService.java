package com.deltaglobal.testebackend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serviço responsável pelo cálculo da taxa de transferência.
 *
 * Regras de negócio (conforme README):
 *  - Transferências de até R$ 100,00 (10000 centavos) são ISENTAS.
 *  - Acima disso: 1% do valor transferido.
 *  - Taxa mínima: R$ 1,00 (100 centavos).
 *  - Taxa máxima (teto): R$ 20,00 (2000 centavos).
 *  - Arredondamento ao centavo mais próximo (HALF_UP via Math.round).
 *  - Depósitos e estornos são SEMPRE isentos (não passam por este serviço).
 *
 * Casos de referência:
 *  R$ 10,00  → R$ 0,00 (isento)
 *  R$ 100,00 → R$ 0,00 (isento, o limite é EXCLUSIVO)
 *  R$ 100,01 → R$ 1,00 (mínimo aplicado)
 *  R$ 150,00 → R$ 1,50
 *  R$ 5.000  → R$ 20,00 (teto aplicado)
 */
@Service
public class TaxaService {

    // Limite de isenção: R$ 100,00 em centavos
    private static final long LIMITE_ISENCAO_CENTAVOS = 10_000L;
    // Taxa mínima: R$ 1,00 em centavos
    private static final long TAXA_MINIMA_CENTAVOS = 100L;
    // Teto da taxa: R$ 20,00 em centavos
    private static final long TAXA_MAXIMA_CENTAVOS = 2_000L;
    // Percentual: 1%
    private static final BigDecimal PERCENTUAL_TAXA = new BigDecimal("0.01");

    /**
     * Calcula a taxa a ser cobrada para um dado valor de transferência.
     *
     * @param valorCentavos Valor da transferência em centavos (deve ser positivo).
     * @return A taxa em centavos. Retorna 0 se isento.
     */
    public Long calcularTaxa(Long valorCentavos) {
        // Transferências até R$ 100,00 são isentas
        if (valorCentavos <= LIMITE_ISENCAO_CENTAVOS) {
            return 0L;
        }

        // Calcula 1% e arredonda ao centavo mais próximo
        BigDecimal valor = BigDecimal.valueOf(valorCentavos);
        long taxa = valor.multiply(PERCENTUAL_TAXA)
                         .setScale(0, RoundingMode.HALF_UP)
                         .longValue();

        // Aplica o mínimo de R$ 1,00
        if (taxa < TAXA_MINIMA_CENTAVOS) {
            return TAXA_MINIMA_CENTAVOS;
        }

        // Aplica o teto de R$ 20,00
        if (taxa > TAXA_MAXIMA_CENTAVOS) {
            return TAXA_MAXIMA_CENTAVOS;
        }

        return taxa;
    }
}
