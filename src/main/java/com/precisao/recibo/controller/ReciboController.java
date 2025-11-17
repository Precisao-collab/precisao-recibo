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
            // Captura o IP do cliente
            String ipCliente = obterIpCliente(httpRequest);
            
            // Gera o PDF do recibo com QR Code
            byte[] pdfBytes = pdfGeracaoService.gerarReciboPDF(request.dadosRecibo(), request.nomeDestinatario(), ipCliente);
            
            // Calcula os valores
            var dadosRecibo = request.dadosRecibo();
            var valorBruto = dadosRecibo.valorBruto();
            var valorInss = calcularINSSComTipo(valorBruto, dadosRecibo.tipoImposto());
            var valorLiquido = calculoService.calcularValorLiquido(valorBruto, valorInss);
            
            // Extrai dígitos da conta e agência
            String agenciaNumero = dadosRecibo.agencia();
            String agenciaDigito = "";
            if (dadosRecibo.agencia() != null && dadosRecibo.agencia().contains("-")) {
                String[] partes = dadosRecibo.agencia().split("-");
                agenciaNumero = partes[0];
                if (partes.length > 1) {
                    agenciaDigito = partes[1];
                }
            }
            
            String contaNumero = dadosRecibo.conta();
            String contaDigito = "";
            if (dadosRecibo.conta() != null && dadosRecibo.conta().contains("-")) {
                String[] partes = dadosRecibo.conta().split("-");
                contaNumero = partes[0];
                if (partes.length > 1) {
                    contaDigito = partes[1];
                }
            }
            
            // Formata data de vencimento (usando data atual como padrão)
            String vencimento = java.time.LocalDate.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.of("pt", "BR"))
            );
            
            // Formata a chave PIX conforme o tipo
            String chavePixFormatada = formatarChavePix(dadosRecibo.tipoChavePix(), dadosRecibo.chavePix());
            
            // Envia o email com o PDF anexado usando o novo template HTML de pagamento
            emailService.enviarReciboEmailCompleto(
                    request.emailDestinatario(),
                    request.nomeDestinatario(),
                    request.assunto(),
                    pdfBytes,
                    dadosRecibo.nomePrestador(),
                    dadosRecibo.cpf(),
                    dadosRecibo.condominio(),
                    dadosRecibo.codigoEmpreendimento(), // Código apenas para o email
                    valorBruto,
                    valorInss,
                    valorLiquido,
                    vencimento,
                    contaNumero, // contaContabil recebe o número da conta
                    dadosRecibo.descricaoServico(),
                    dadosRecibo.nomeBanco(),
                    agenciaNumero,
                    agenciaDigito,
                    contaNumero,
                    contaDigito,
                    chavePixFormatada
            );
            
            response.put("sucesso", true);
            response.put("mensagem", "Recibo enviado com sucesso para centraldepagamentos@precisaoadm.com.br (solicitado por " + request.nomeDestinatario() + ")");
            response.put("emailDestinatario", "centraldepagamentos@precisaoadm.com.br");
            response.put("emailSolicitante", request.emailDestinatario());
            response.put("nomeSolicitante", request.nomeDestinatario());
            
            return ResponseEntity.status(HttpStatus.OK).body(response);
            
        } catch (IllegalArgumentException e) {
            response.put("sucesso", false);
            response.put("mensagem", "Erro de validação: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("sucesso", false);
            response.put("mensagem", "Erro ao enviar recibo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private java.math.BigDecimal calcularINSSComTipo(java.math.BigDecimal valorBruto, String tipoImposto) {
        if (tipoImposto == null || "SEM_INSS".equalsIgnoreCase(tipoImposto)) {
            return java.math.BigDecimal.ZERO;
        }
        return calculoService.calcularINSS(valorBruto);
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

