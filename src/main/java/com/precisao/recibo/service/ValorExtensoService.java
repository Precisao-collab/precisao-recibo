package com.precisao.recibo.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ValorExtensoService {

    private static final String[] UNIDADES = {"", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove"};
    private static final String[] DEZ_A_DEZENOVE = {"dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"};
    private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"};
    private static final String[] CENTENAS = {"", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos"};

    public String converter(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) {
            return "zero reais";
        }

        long parteInteira = valor.longValue();
        int centavos = valor.remainder(BigDecimal.ONE).multiply(new BigDecimal(100)).intValue();

        StringBuilder resultado = new StringBuilder();

        if (parteInteira == 0) {
            resultado.append("zero reais");
        } else {
            resultado.append(converterParteInteira(parteInteira));
            resultado.append(parteInteira == 1 ? " real" : " reais");
        }

        if (centavos > 0) {
            resultado.append(" e ");
            resultado.append(converterCentavos(centavos));
            resultado.append(centavos == 1 ? " centavo" : " centavos");
        }

        return resultado.toString();
    }

    private String converterParteInteira(long numero) {
        if (numero == 0) return "";
        if (numero < 0) return "menos " + converterParteInteira(-numero);

        if (numero < 10) return UNIDADES[(int) numero];
        if (numero < 20) return DEZ_A_DEZENOVE[(int) numero - 10];
        if (numero < 100) return converterDezenas((int) numero);
        if (numero < 1000) return converterCentenas((int) numero);
        if (numero < 1000000) return converterMilhares(numero);
        if (numero < 1000000000) return converterMilhoes(numero);

        return converterBilhoes(numero);
    }

    private String converterDezenas(int numero) {
        int dezena = numero / 10;
        int unidade = numero % 10;
        if (unidade == 0) {
            return DEZENAS[dezena];
        }
        return DEZENAS[dezena] + " e " + UNIDADES[unidade];
    }

    private String converterCentenas(int numero) {
        if (numero == 100) return "cem";
        int centena = numero / 100;
        int resto = numero % 100;
        if (resto == 0) {
            return CENTENAS[centena];
        }
        return CENTENAS[centena] + " e " + converterParteInteira(resto);
    }

    private String converterMilhares(long numero) {
        long milhar = numero / 1000;
        long resto = numero % 1000;

        String textoMilhar = converterParteInteira(milhar);
        String resultado = milhar == 1 ? "mil" : textoMilhar + " mil";

        if (resto > 0) {
            if (resto < 100) {
                resultado += " e " + converterParteInteira(resto);
            } else {
                resultado += " " + converterParteInteira(resto);
            }
        }
        return resultado;
    }

    private String converterMilhoes(long numero) {
        long milhao = numero / 1000000;
        long resto = numero % 1000000;

        String textoMilhao = converterParteInteira(milhao);
        String resultado = milhao == 1 ? "um milhão" : textoMilhao + " milhões";

        if (resto > 0) {
            if (resto < 100) {
                resultado += " e " + converterParteInteira(resto);
            } else {
                resultado += " " + converterParteInteira(resto);
            }
        }
        return resultado;
    }

    private String converterBilhoes(long numero) {
        long bilhao = numero / 1000000000;
        long resto = numero % 1000000000;

        String textoBilhao = converterParteInteira(bilhao);
        String resultado = bilhao == 1 ? "um bilhão" : textoBilhao + " bilhões";

        if (resto > 0) {
            if (resto < 100) {
                resultado += " e " + converterParteInteira(resto);
            } else {
                resultado += " " + converterParteInteira(resto);
            }
        }
        return resultado;
    }

    private String converterCentavos(int centavos) {
        return converterParteInteira(centavos);
    }
}





