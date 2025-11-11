package com.precisao.recibo.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CalculoService {

    // Alíquota de INSS para prestadores de serviço (11%)
    private static final BigDecimal ALIQUOTA_INSS = new BigDecimal("0.11");

    public BigDecimal calcularINSS(BigDecimal valorBruto) {
        if (valorBruto == null || valorBruto.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valorBruto.multiply(ALIQUOTA_INSS).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularValorLiquido(BigDecimal valorBruto, BigDecimal valorINSS) {
        if (valorBruto == null) {
            return BigDecimal.ZERO;
        }
        if (valorINSS == null) {
            valorINSS = BigDecimal.ZERO;
        }
        return valorBruto.subtract(valorINSS).setScale(2, RoundingMode.HALF_UP);
    }
}





