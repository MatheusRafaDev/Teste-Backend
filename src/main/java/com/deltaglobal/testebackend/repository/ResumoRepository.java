package com.deltaglobal.testebackend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Map;

@Repository
public class ResumoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ResumoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResumoDto getResumo(ZonedDateTime fromTime) {
        ResumoDto dto = new ResumoDto();
        Timestamp fromTimestamp = Timestamp.from(fromTime.toInstant());
        
        // contasAtivasAgora
        Integer contasAtivas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conta WHERE estado = 'ATIVA' AND usuario_id IS NOT NULL", Integer.class);
        dto.setContasAtivasAgora(contasAtivas != null ? contasAtivas : 0);

        // saldoTotalUsuariosAgora (in reais)
        Long saldoTotal = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(saldo_centavos), 0) FROM conta WHERE usuario_id IS NOT NULL", Long.class);
        dto.setSaldoTotalUsuariosAgora(saldoTotal != null ? BigDecimal.valueOf(saldoTotal, 2) : BigDecimal.ZERO);

        // agendamentosPendentesAgora
        Integer agendamentos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agendamento WHERE estado = 'AGENDADO'", Integer.class);
        dto.setAgendamentosPendentesAgora(agendamentos != null ? agendamentos : 0);

        // Janela
        Map<String, Object> janelaData = jdbcTemplate.queryForMap(
            "SELECT COUNT(*) as qtd_transf, " +
            "COALESCE(SUM(valor_centavos), 0) as soma_valor, " +
            "COALESCE(SUM(taxa_centavos), 0) as soma_taxa, " +
            "COALESCE(AVG(valor_centavos), 0) as media_valor " +
            "FROM transferencia WHERE estado = 'CONFIRMADA' AND criada_em >= ?", fromTimestamp);

        Number qtdTransf = (Number) janelaData.get("qtd_transf");
        Number somaValor = (Number) janelaData.get("soma_valor");
        Number somaTaxa = (Number) janelaData.get("soma_taxa");
        Number mediaValor = (Number) janelaData.get("media_valor");

        dto.setTransferenciasNaJanela(qtdTransf.intValue());
        dto.setValorTransferidoNaJanela(BigDecimal.valueOf(somaValor.longValue(), 2));
        dto.setTaxasNaJanela(BigDecimal.valueOf(somaTaxa.longValue(), 2));
        
        // Fix average scale
        dto.setTicketMedioNaJanela(BigDecimal.valueOf(mediaValor.doubleValue() / 100).setScale(2, java.math.RoundingMode.HALF_EVEN));

        // estornosNaJanela
        Integer estornos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transferencia WHERE estado = 'ESTORNADA' AND transferencia_original_id IS NULL AND concluida_em >= ?", Integer.class, fromTimestamp);
        dto.setEstornosNaJanela(estornos != null ? estornos : 0);

        // somaDosMovimentosEhZero
        Long somaMovimentos = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(CASE WHEN tipo = 'ENTRADA' THEN valor_centavos ELSE -valor_centavos END), 0) FROM movimento", Long.class);
        dto.setSomaDosMovimentosEhZero(somaMovimentos != null && somaMovimentos == 0L);

        return dto;
    }
}
