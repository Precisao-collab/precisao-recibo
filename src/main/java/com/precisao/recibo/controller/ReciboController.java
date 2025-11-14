package com.precisao.recibo.controller;

import com.precisao.recibo.dto.EnviarReciboEmailRequest;
import com.precisao.recibo.dto.ReciboRequest;
import com.precisao.recibo.service.CalculoService;
import com.precisao.recibo.service.EmailService;
import com.precisao.recibo.service.PdfGeracaoService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<Map<String, Object>> enviarReciboEmail(@RequestBody EnviarReciboEmailRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Gera o PDF do recibo
            byte[] pdfBytes = pdfGeracaoService.gerarReciboPDF(request.dadosRecibo());
            
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
                    null, // contaContabil - pode ser adicionado depois
                    dadosRecibo.descricaoServico(),
                    dadosRecibo.nomeBanco(),
                    agenciaNumero,
                    agenciaDigito,
                    contaNumero,
                    contaDigito,
                    chavePixFormatada
            );
            
            response.put("sucesso", true);
            response.put("mensagem", "Recibo enviado com sucesso para " + request.emailDestinatario());
            response.put("emailDestinatario", request.emailDestinatario());
            
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
}

