package com.precisao.recibo.controller;

import com.precisao.recibo.dto.EnviarReciboEmailRequest;
import com.precisao.recibo.dto.ReciboRequest;
import com.precisao.recibo.service.CalculoService;
import com.precisao.recibo.service.EmailService;
import com.precisao.recibo.service.PdfGeracaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/recibos")
public class ReciboController {

    private final PdfGeracaoService pdfGeracaoService;
    private final EmailService emailService;
    private final CalculoService calculoService;

    public ReciboController(PdfGeracaoService pdfGeracaoService, EmailService emailService, CalculoService calculoService) {
        this.pdfGeracaoService = pdfGeracaoService;
        this.emailService = emailService;
        this.calculoService = calculoService;
    }

    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> criarRecibo(@RequestBody ReciboRequest request) {
        try {
            byte[] pdfBytes = pdfGeracaoService.gerarReciboPDF(request);
            
            ByteArrayResource resource = new ByteArrayResource(pdfBytes);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "recibo.pdf");
            headers.setContentLength(pdfBytes.length);
            
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/enviar-email", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> enviarReciboEmail(@RequestBody EnviarReciboEmailRequest request, HttpServletRequest httpRequest) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("=== INÍCIO DO ENVIO DE EMAIL ===");
            System.out.println("Destinatário: " + request.emailDestinatario());
            System.out.println("Nome Destinatário: " + request.nomeDestinatario());
            
            // Captura o IP do cliente
            String ipCliente = obterIpCliente(httpRequest);
            System.out.println("IP Cliente: " + ipCliente);
            
            // Calcula os valores
            var dadosRecibo = request.dadosRecibo();
            var valorBruto = dadosRecibo.valorBruto();
            var valorInss = calcularINSSComTipo(valorBruto, dadosRecibo.tipoImposto());
            var valorLiquido = calculoService.calcularValorLiquido(valorBruto, valorInss);
            
            // Lê o número de parcelas
            int numeroParcelas = 1;
            if (dadosRecibo.parcelas() != null && !dadosRecibo.parcelas().isBlank()) {
                try {
                    numeroParcelas = Integer.parseInt(dadosRecibo.parcelas());
                } catch (NumberFormatException e) {
                    System.err.println("Erro ao parsear número de parcelas: " + dadosRecibo.parcelas() + ". Usando padrão: 1");
                }
            }
            
            // Parseia a data base do recibo
            java.time.LocalDate dataBase = java.time.LocalDate.now();
            if (dadosRecibo.data() != null && !dadosRecibo.data().isBlank()) {
                try {
                    dataBase = java.time.LocalDate.parse(dadosRecibo.data());
                } catch (Exception e) {
                    System.err.println("Erro ao parsear data: " + dadosRecibo.data() + ". Usando data atual.");
                }
            }
            
            // Gera múltiplos PDFs baseado no número de parcelas
            System.out.println("Gerando " + numeroParcelas + " PDF(s)...");
            java.util.Map<String, byte[]> pdfsRecibos = new java.util.LinkedHashMap<>();
            
            for (int i = 0; i < numeroParcelas; i++) {
                // Calcula a data de vencimento para esta parcela (incrementa meses)
                java.time.LocalDate dataVencimento = dataBase.plusMonths(i);
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
            System.out.println("=== FIM DO ENVIO DE EMAIL ===");
            
            response.put("sucesso", true);
            String mensagemParcelas = numeroParcelas > 1 
                    ? numeroParcelas + " recibos" 
                    : "1 recibo";
            response.put("mensagem", mensagemParcelas + " enviado(s) com sucesso para centraldepagamentos@precisaoadm.com.br (solicitado por " + request.nomeDestinatario() + ")");
            response.put("emailDestinatario", "centraldepagamentos@precisaoadm.com.br");
            response.put("numeroParcelas", numeroParcelas);
            response.put("emailSolicitante", request.emailDestinatario());
            response.put("nomeSolicitante", request.nomeDestinatario());
            
            return ResponseEntity.status(HttpStatus.OK).body(response);
            
        } catch (IllegalArgumentException e) {
            response.put("sucesso", false);
            response.put("mensagem", "Erro de validação: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ERRO ao enviar recibo:");
            System.err.println("Mensagem: " + e.getMessage());
            System.err.println("Causa: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
            e.printStackTrace();
            
            String mensagemErro = "Erro ao enviar recibo";
            if (e.getMessage() != null) {
                mensagemErro += ": " + e.getMessage();
            }
            
            response.put("sucesso", false);
            response.put("mensagem", mensagemErro);
            response.put("erro", e.getClass().getSimpleName());
            if (e.getCause() != null) {
                response.put("causa", e.getCause().getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private java.math.BigDecimal calcularINSSComTipo(java.math.BigDecimal valorBruto, String tipoImposto) {
        if (tipoImposto == null || "SEM_INSS".equalsIgnoreCase(tipoImposto)) {
            return java.math.BigDecimal.ZERO;
        }
        return calculoService.calcularINSS(valorBruto);
    }

    private String gerarNomeArquivoComParcela(String nomePrestador, int numeroParcela, int totalParcelas, java.time.LocalDate dataVencimento) {
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

    private String obterIpCliente(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Se houver múltiplos IPs, pega o primeiro
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "0.0.0.0";
    }

    @GetMapping("/qr-info")
    public ResponseEntity<String> exibirInfoQRCode(
            @RequestParam(required = false) String gerente,
            @RequestParam(required = false) String data,
            @RequestParam(required = false) String hora) {
        
        // Monta a data e hora completa
        String dataHoraCompleta = "";
        if (data != null && hora != null) {
            dataHoraCompleta = data + " às " + hora;
        } else if (data != null) {
            dataHoraCompleta = data;
        } else if (hora != null) {
            dataHoraCompleta = hora;
        } else {
            dataHoraCompleta = "Não informado";
        }
        
        String html = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Informações do Recibo - Precisão</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        max-width: 600px;
                        margin: 50px auto;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    .container {
                        background-color: white;
                        padding: 30px;
                        border-radius: 10px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    h1 {
                        color: #ED1A3B;
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .info-box {
                        background-color: #f8f9fa;
                        padding: 15px;
                        margin: 10px 0;
                        border-left: 4px solid #ED1A3B;
                        border-radius: 4px;
                    }
                    .label {
                        font-weight: bold;
                        color: #666;
                        display: inline-block;
                        min-width: 120px;
                    }
                    .value {
                        color: #333;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Informações do Recibo</h1>
                    <div class="info-box">
                        <span class="label">Gerente:</span>
                        <span class="value">%s</span>
                    </div>
                    <div class="info-box">
                        <span class="label">Data e Hora de Geração:</span>
                        <span class="value">%s</span>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                gerente != null ? gerente : "Não informado",
                dataHoraCompleta
            );
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }
}

