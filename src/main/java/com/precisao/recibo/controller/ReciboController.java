package com.precisao.recibo.controller;

import com.precisao.recibo.dto.EnviarReciboEmailRequest;
import com.precisao.recibo.dto.ReciboRequest;
import com.precisao.recibo.service.CalculoService;
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
    private final CalculoService calculoService;
    private final com.precisao.recibo.service.ReciboProcessamentoService reciboProcessamentoService;
    private final com.precisao.recibo.service.CpfValidacaoService cpfValidacaoService;

    public ReciboController(
            PdfGeracaoService pdfGeracaoService,
            CalculoService calculoService,
            com.precisao.recibo.service.ReciboProcessamentoService reciboProcessamentoService,
            com.precisao.recibo.service.CpfValidacaoService cpfValidacaoService) {
        this.pdfGeracaoService = pdfGeracaoService;
        this.calculoService = calculoService;
        this.reciboProcessamentoService = reciboProcessamentoService;
        this.cpfValidacaoService = cpfValidacaoService;
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
            System.out.println("=== INÍCIO DA REQUISIÇÃO ===");
            System.out.println("Destinatário: " + request.emailDestinatario());
            System.out.println("Nome Destinatário: " + request.nomeDestinatario());
            
            // Captura o IP do cliente
            String ipCliente = obterIpCliente(httpRequest);
            System.out.println("IP Cliente: " + ipCliente);
            
            // Validação rápida dos dados
            var dadosRecibo = request.dadosRecibo();
            
            // Calcula os valores para validação
            var valorBruto = dadosRecibo.valorBruto();
            var valorInss = calcularINSSComTipo(valorBruto, dadosRecibo.tipoImposto());
            var valorLiquido = calculoService.calcularValorLiquido(valorBruto, valorInss);
            
            // Lê o número de parcelas
            int numeroParcelas = 1;
            if (dadosRecibo.parcelas() != null && !dadosRecibo.parcelas().isBlank()) {
                try {
                    numeroParcelas = Integer.parseInt(dadosRecibo.parcelas());
                    if (numeroParcelas < 1 || numeroParcelas > 12) {
                        response.put("sucesso", false);
                        response.put("mensagem", "Número de parcelas deve estar entre 1 e 12");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                    }
                } catch (NumberFormatException e) {
                    response.put("sucesso", false);
                    response.put("mensagem", "Número de parcelas inválido");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
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
            
            // Processa tudo em background de forma assíncrona
            reciboProcessamentoService.processarRecibosAssincrono(
                    request,
                    ipCliente,
                    dadosRecibo,
                    valorBruto,
                    valorInss,
                    valorLiquido,
                    numeroParcelas,
                    dataBase
            );
            
            System.out.println("Processamento iniciado em background. Retornando resposta imediata.");
            System.out.println("=== RESPOSTA ENVIADA ===");
            
            // Retorna resposta imediata ao usuário
            response.put("sucesso", true);
            String mensagemParcelas = numeroParcelas > 1 
                    ? numeroParcelas + " recibos" 
                    : "1 recibo";
            response.put("mensagem", mensagemParcelas + " sendo processado(s) e enviado(s) para centraldepagamentos@precisaoadm.com.br (solicitado por " + request.nomeDestinatario() + ")");
            response.put("emailDestinatario", "centraldepagamentos@precisaoadm.com.br");
            response.put("numeroParcelas", numeroParcelas);
            response.put("emailSolicitante", request.emailDestinatario());
            response.put("nomeSolicitante", request.nomeDestinatario());
            response.put("processando", true);
            
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

    @GetMapping("/validar-cpf")
    public ResponseEntity<Map<String, Object>> validarCpf(@RequestParam String cpf) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean cpfValido = cpfValidacaoService.validarCpfNoGoverno(cpf);
            response.put("valido", cpfValido);
            response.put("mensagem", cpfValido ? "CPF válido" : "CPF inválido ou não encontrado na base do governo");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("valido", false);
            response.put("mensagem", "Erro ao validar CPF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

