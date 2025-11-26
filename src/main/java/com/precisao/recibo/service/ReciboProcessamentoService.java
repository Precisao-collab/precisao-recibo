package com.precisao.recibo.service;

import com.precisao.recibo.dto.EnviarReciboEmailRequest;
import com.precisao.recibo.dto.ReciboRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ReciboProcessamentoService {

    private final PdfGeracaoService pdfGeracaoService;
    private final EmailService emailService;

    public ReciboProcessamentoService(PdfGeracaoService pdfGeracaoService, EmailService emailService) {
        this.pdfGeracaoService = pdfGeracaoService;
        this.emailService = emailService;
    }

    @Async("emailExecutor")
    public void processarRecibosAssincrono(
            EnviarReciboEmailRequest request,
            String ipCliente,
            ReciboRequest dadosRecibo,
            BigDecimal valorBruto,
            BigDecimal valorInss,
            BigDecimal valorLiquido,
            int numeroParcelas,
            LocalDate dataBase) {
        
        try {
            System.out.println("=== INÍCIO DO PROCESSAMENTO ASSÍNCRONO ===");
            System.out.println("Processando " + numeroParcelas + " recibo(s) em background...");
            
            // Gera múltiplos PDFs baseado no número de parcelas
            java.util.Map<String, byte[]> pdfsRecibos = new java.util.LinkedHashMap<>();
            
            for (int i = 0; i < numeroParcelas; i++) {
                // Calcula a data de vencimento para esta parcela (incrementa meses)
                LocalDate dataVencimento = dataBase.plusMonths(i);
                String dataVencimentoStr = dataVencimento.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                
                // Gera o PDF do recibo com QR Code, data específica e número da parcela
                byte[] pdfBytes = pdfGeracaoService.gerarReciboPDF(
                        dadosRecibo, 
                        request.nomeDestinatario(), 
                        ipCliente,
                        dataVencimentoStr,
                        i + 1 // número da parcela (1-indexed)
                );
                
                // Gera nome do arquivo com número da parcela
                String nomeArquivo = gerarNomeArquivoComParcela(dadosRecibo.nomePrestador(), i + 1, numeroParcelas, dataVencimento);
                pdfsRecibos.put(nomeArquivo, pdfBytes);
                System.out.println("PDF " + (i + 1) + "/" + numeroParcelas + " gerado com sucesso. Tamanho: " + pdfBytes.length + " bytes");
            }
            
            // Extrai dígitos da conta e agência (se fornecidos)
            String agenciaNumero = null;
            String agenciaDigito = null;
            if (dadosRecibo.agencia() != null && !dadosRecibo.agencia().isBlank()) {
                if (dadosRecibo.agencia().contains("-")) {
                    String[] partes = dadosRecibo.agencia().split("-");
                    agenciaNumero = partes[0];
                    if (partes.length > 1) {
                        agenciaDigito = partes[1];
                    }
                } else {
                    agenciaNumero = dadosRecibo.agencia();
                }
            }
            
            String contaNumero = null;
            String contaDigito = null;
            if (dadosRecibo.conta() != null && !dadosRecibo.conta().isBlank()) {
                if (dadosRecibo.conta().contains("-")) {
                    String[] partes = dadosRecibo.conta().split("-");
                    contaNumero = partes[0];
                    if (partes.length > 1) {
                        contaDigito = partes[1];
                    }
                } else {
                    contaNumero = dadosRecibo.conta();
                }
            }
            
            // Formata data de vencimento da primeira parcela para o email
            String vencimento = dataBase.format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.of("pt", "BR"))
            );
            
            // Formata a chave PIX conforme o tipo (se fornecida)
            String chavePixFormatada = null;
            if (dadosRecibo.tipoChavePix() != null && dadosRecibo.chavePix() != null && !dadosRecibo.chavePix().isBlank()) {
                chavePixFormatada = formatarChavePix(dadosRecibo.tipoChavePix(), dadosRecibo.chavePix());
            }
            
            // Envia o email com múltiplos PDFs anexados
            System.out.println("Enviando email com " + numeroParcelas + " anexo(s)...");
            emailService.enviarReciboEmailComMultiplosAnexos(
                    request.emailDestinatario(),
                    request.nomeDestinatario(),
                    request.assunto(),
                    pdfsRecibos,
                    dadosRecibo.nomePrestador(),
                    dadosRecibo.cpf(),
                    dadosRecibo.condominio(),
                    dadosRecibo.codigoEmpreendimento(),
                    valorBruto,
                    valorInss,
                    valorLiquido,
                    vencimento,
                    contaNumero,
                    dadosRecibo.descricaoServico(),
                    dadosRecibo.nomeBanco(),
                    agenciaNumero,
                    agenciaDigito,
                    contaNumero,
                    contaDigito,
                    chavePixFormatada
            );
            
            System.out.println("Email com " + numeroParcelas + " recibo(s) enviado com sucesso!");
            System.out.println("=== FIM DO PROCESSAMENTO ASSÍNCRONO ===");
            
        } catch (Exception e) {
            System.err.println("ERRO no processamento assíncrono:");
            System.err.println("Mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String gerarNomeArquivoComParcela(String nomePrestador, int numeroParcela, int totalParcelas, LocalDate dataVencimento) {
        String dataFormatada = dataVencimento.format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.of("pt", "BR"))
        );

        String nomeArquivoBase = "Recibo_ProLabore";
        
        if (nomePrestador != null && !nomePrestador.isBlank()) {
            String nomeSanitizado = nomePrestador
                    .replaceAll("[^a-zA-Z0-9\\s]", "")
                    .replaceAll("\\s+", "_")
                    .trim();
            if (!nomeSanitizado.isEmpty()) {
                nomeArquivoBase += "_" + nomeSanitizado;
            }
        }
        
        // Adiciona número da parcela se houver mais de uma
        if (totalParcelas > 1) {
            nomeArquivoBase += "_Parcela" + numeroParcela + "de" + totalParcelas;
        }
        
        return nomeArquivoBase + "_" + dataFormatada + ".pdf";
    }

    private String formatarChavePix(String tipo, String chave) {
        if (chave == null || chave.isBlank()) {
            return "";
        }
        if ("cpf".equalsIgnoreCase(tipo)) {
            // Remove formatação se já existir e formata como CPF
            String cpfLimpo = chave.replaceAll("[^0-9]", "");
            if (cpfLimpo.length() == 11) {
                return String.format("%s.%s.%s-%s",
                        cpfLimpo.substring(0, 3),
                        cpfLimpo.substring(3, 6),
                        cpfLimpo.substring(6, 9),
                        cpfLimpo.substring(9, 11));
            }
        } else if ("celular".equalsIgnoreCase(tipo)) {
            // Remove formatação se já existir
            String celularLimpo = chave.replaceAll("[^0-9]", "");
            if (celularLimpo.length() == 11) {
                return String.format("(%s) %s-%s",
                        celularLimpo.substring(0, 2),
                        celularLimpo.substring(2, 7),
                        celularLimpo.substring(7, 11));
            }
        }
        return chave;
    }
}


