package com.precisao.recibo.dto;

import java.math.BigDecimal;

public record ReciboRequest(
        String condominio,
        String cnpjCondominio,
        String pis,
        String codigoBanco,
        String agencia,
        String conta,
        String tipoChavePix,
        String chavePix,
        String nomePrestador,
        String cpf,
        BigDecimal valorBruto,
        String descricaoServico
) {
}

