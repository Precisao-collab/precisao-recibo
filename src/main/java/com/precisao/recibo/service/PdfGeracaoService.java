package com.precisao.recibo.service;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.precisao.recibo.dto.ReciboRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PdfGeracaoService {

    private static final String TEMPLATE_PATH = "templates/PRO-LABORE-RECIBO.pdf";

    private final ValorExtensoService valorExtensoService;
    private final CalculoService calculoService;

    public PdfGeracaoService(ValorExtensoService valorExtensoService, CalculoService calculoService) {
        this.valorExtensoService = valorExtensoService;
        this.calculoService = calculoService;
    }

    public byte[] gerarReciboPDF(ReciboRequest request) throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);

        try (InputStream templateStream = resource.getInputStream()) {
            byte[] templateBytes = templateStream.readAllBytes();

            BigDecimal valorBruto = request.valorBruto();
            BigDecimal valorINSS = calculoService.calcularINSS(valorBruto);
            BigDecimal valorLiquido = calculoService.calcularValorLiquido(valorBruto, valorINSS);
            String valorPorExtenso = valorExtensoService.converter(valorBruto);

            Map<String, String> valores = construirMapaDeValores(request, valorBruto, valorINSS, valorLiquido, valorPorExtenso);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (PdfDocument pdfDocument = new PdfDocument(
                    new PdfReader(new ByteArrayInputStream(templateBytes)),
                    new PdfWriter(outputStream)
            )) {
                PdfAcroForm acroForm = PdfAcroForm.getAcroForm(pdfDocument, true);
                Map<String, PdfFormField> campos = acroForm.getAllFormFields();

                // Mapeamento dos campos do PDF para os valores
                Map<String, String> mapeamentoCampos = criarMapeamentoCampos(valores);

                campos.forEach((nomeCampo, campo) -> {
                    String valor = mapeamentoCampos.get(nomeCampo);
                    if (valor != null) {
                        campo.setValue(valor);
                        System.out.printf("[PDF] Campo preenchido: %s = %s%n", nomeCampo, valor);
                    } else {
                        System.out.printf("[PDF] Campo sem mapeamento: %s%n", nomeCampo);
                    }
                });

                acroForm.flattenFields();
            }

            return outputStream.toByteArray();
        }
    }

    private Map<String, String> criarMapeamentoCampos(Map<String, String> valores) {
        Map<String, String> mapeamento = new HashMap<>();
        
        // Baseado na estrutura do PDF mostrada:
        // Linha 1: PRO-LABORE RECIBO | Mês de referência
        // Linha 2: Condomínio | CNPJ
        // Linha 3: Recebi da Empresa acima identificada
        // Linha 4: Dados Bancários (CPF, Banco, Agência, Conta, Chave pix) | Especificação (Descontos: INSS, Valor Líquido)
        
        mapeamento.put("textarea_1hoqn", valores.get("condominio"));        // Condomínio
        mapeamento.put("textarea_2ywas", valores.get("cnpj"));              // CNPJ
        mapeamento.put("textarea_3ccnr", valores.get("valorbruto"));        // Valor recebido
        mapeamento.put("textarea_4nu", valores.get("cpf"));                 // CPF
        mapeamento.put("textarea_5gvf", valores.get("banco"));              // Banco
        mapeamento.put("textarea_6zzrb", valores.get("agencia"));           // Agência
        mapeamento.put("textarea_7uhfa", valores.get("conta"));             // Conta
        mapeamento.put("textarea_8pofg", valores.get("pix"));               // Chave pix
        mapeamento.put("textarea_9zjdj", valores.get("descricao"));         // Especificação/Descrição
        mapeamento.put("textarea_10dvxp", valores.get("inss"));             // INSS
        mapeamento.put("textarea_11vojl", valores.get("valorliquido"));     // Valor Líquido
        mapeamento.put("textarea_12pmv", valores.get("mesreferencia"));     // Mês de referência
        
        return mapeamento;
    }

    private Map<String, String> construirMapaDeValores(ReciboRequest request,
                                                       BigDecimal valorBruto,
                                                       BigDecimal valorINSS,
                                                       BigDecimal valorLiquido,
                                                       String valorPorExtenso) {
        Map<String, String> valores = new HashMap<>();
        
        String mesReferencia = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM/yyyy", Locale.of("pt", "BR")));

        valores.put("condominio", request.condominio());
        valores.put("cnpj", formatarCNPJ(request.cnpjCondominio()));
        valores.put("valorbruto", formatarMoeda(valorBruto));
        valores.put("extenso", valorPorExtenso);
        valores.put("cpf", formatarCPF(request.cpf()));
        valores.put("banco", request.codigoBanco());
        valores.put("agencia", request.agencia());
        valores.put("conta", request.conta());
        valores.put("pix", formatarChavePixCompleta(request.tipoChavePix(), request.chavePix()));
        valores.put("descricao", request.descricaoServico());
        valores.put("inss", formatarMoeda(valorINSS));
        valores.put("valorliquido", formatarMoeda(valorLiquido));
        valores.put("prestador", request.nomePrestador());
        valores.put("pis", request.pis());
        valores.put("mesreferencia", mesReferencia);

        return valores;
    }


    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
        return currencyFormat.format(valor);
    }

    private String formatarCNPJ(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj == null ? "" : cnpj;
        }
        return String.format("%s.%s.%s/%s-%s",
                cnpj.substring(0, 2),
                cnpj.substring(2, 5),
                cnpj.substring(5, 8),
                cnpj.substring(8, 12),
                cnpj.substring(12, 14));
    }

    private String formatarCPF(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf == null ? "" : cpf;
        }
        return String.format("%s.%s.%s-%s",
                cpf.substring(0, 3),
                cpf.substring(3, 6),
                cpf.substring(6, 9),
                cpf.substring(9, 11));
    }

    private String formatarChavePixCompleta(String tipo, String chave) {
        if (chave == null || chave.isBlank()) {
            return "";
        }
        String chaveFormatada = chave;
        if ("cpf".equalsIgnoreCase(tipo)) {
            chaveFormatada = formatarCPF(chave);
        } else if ("celular".equalsIgnoreCase(tipo) && chave.length() == 11) {
            chaveFormatada = String.format("(%s) %s-%s",
                    chave.substring(0, 2),
                    chave.substring(2, 7),
                    chave.substring(7, 11));
        }

        if (tipo == null || tipo.isBlank()) {
            return chaveFormatada;
        }

        return switch (tipo.toLowerCase(Locale.ROOT)) {
            case "cpf" -> "CPF " + chaveFormatada;
            case "celular" -> "Celular " + chaveFormatada;
            case "email" -> "E-mail " + chaveFormatada;
            case "chave aleatoria", "aleatoria", "random" -> "Chave Aleatória " + chaveFormatada;
            default -> chaveFormatada;
        };
    }
}
