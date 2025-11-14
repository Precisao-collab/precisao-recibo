package com.precisao.recibo.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

@Service
public class EmailService {

    private static final String LOGO_PATH = "templates/precisão logo.png";

    private final JavaMailSender mailSender;
    private final String emailRemetente;
    private final String nomeRemetente;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.email.remetente}") String emailRemetente,
            @Value("${app.email.nome-remetente}") String nomeRemetente) {
        this.mailSender = mailSender;
        this.emailRemetente = emailRemetente;
        this.nomeRemetente = nomeRemetente;
    }

    public void enviarReciboEmail(
            String emailDestinatario,
            String nomeDestinatario,
            String assunto,
            byte[] pdfRecibo,
            BigDecimal valorBruto,
            String nomePrestador) throws MessagingException {
        
        enviarReciboEmailCompleto(
                emailDestinatario,
                nomeDestinatario,
                assunto,
                pdfRecibo,
                nomePrestador,
                null,
                null,
                valorBruto,
                null,
                null
        );
    }

    public void enviarReciboEmailCompleto(
            String emailDestinatario,
            String nomeDestinatario,
            String assunto,
            byte[] pdfRecibo,
            String nomePrestador,
            String cpfPrestador,
            String nomeCondominio,
            BigDecimal valorBruto,
            BigDecimal valorInss,
            BigDecimal valorLiquido) throws MessagingException {
        
        enviarReciboEmailCompleto(
                emailDestinatario,
                nomeDestinatario,
                assunto,
                pdfRecibo,
                nomePrestador,
                cpfPrestador,
                nomeCondominio,
                null, // codigoEmpreendimento
                valorBruto,
                valorInss,
                valorLiquido,
                null, // vencimento
                null, // contaContabil
                null, // descricaoPagamento
                null, // nomeBanco
                null, // agencia
                null, // digitoAgencia
                null, // conta
                null, // digitoConta
                null  // chavePix
        );
    }

    public void enviarReciboEmailCompleto(
            String emailDestinatario,
            String nomeDestinatario,
            String assunto,
            byte[] pdfRecibo,
            String nomePrestador,
            String cpfPrestador,
            String nomeCondominio,
            String codigoEmpreendimento,
            BigDecimal valorBruto,
            BigDecimal valorInss,
            BigDecimal valorLiquido,
            String vencimento,
            String contaContabil,
            String descricaoPagamento,
            String nomeBanco,
            String agencia,
            String digitoAgencia,
            String conta,
            String digitoConta,
            String chavePix) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(String.format("%s <%s>", nomeRemetente, emailRemetente));
        helper.setTo(emailDestinatario);
        helper.setSubject(assunto);

        String nomeArquivo = gerarNomeArquivo(nomePrestador);
        String corpoEmail = construirCorpoEmailDoTemplate(
                nomeDestinatario,
                nomePrestador,
                cpfPrestador,
                nomeCondominio,
                codigoEmpreendimento,
                valorBruto,
                valorInss,
                valorLiquido,
                vencimento,
                contaContabil,
                descricaoPagamento,
                nomeBanco,
                agencia,
                digitoAgencia,
                conta,
                digitoConta,
                chavePix
        );
        
        helper.setText(corpoEmail, true);
        
        // Adiciona a logo como recurso inline
        try {
            ClassPathResource logoResource = new ClassPathResource(LOGO_PATH);
            if (logoResource.exists()) {
                helper.addInline("logo", logoResource);
                System.out.println("Logo adicionada como recurso inline (CID)");
            } else {
                System.err.println("Logo não encontrada para adicionar como recurso inline: " + LOGO_PATH);
            }
        } catch (Exception e) {
            System.err.println("Erro ao adicionar logo como recurso inline: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Adiciona o PDF como anexo
        helper.addAttachment(nomeArquivo, new ByteArrayResource(pdfRecibo));

        mailSender.send(message);
    }

    private String construirCorpoEmailDoTemplate(
            String nomeDestinatario,
            String nomePrestador,
            String cpfPrestador,
            String nomeCondominio,
            String codigoEmpreendimento,
            BigDecimal valorBruto,
            BigDecimal valorInss,
            BigDecimal valorLiquido,
            String vencimento,
            String contaContabil,
            String descricaoPagamento,
            String nomeBanco,
            String agencia,
            String digitoAgencia,
            String conta,
            String digitoConta,
            String chavePix) {
        
        try {
            // Carrega o novo template HTML de pagamento
            ClassPathResource resource = new ClassPathResource("templates/email-pagamento-template.html");
            String template;
            
            try (InputStream inputStream = resource.getInputStream()) {
                template = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
            
            // Formata os dados
            String dataAtual = LocalDate.now().format(
                    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("pt", "BR"))
            );
            
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));
            
            // Extrai dígitos da conta e agência se não fornecidos separadamente
            String agenciaNumero = agencia;
            String agenciaDigito = digitoAgencia;
            if (agencia != null && agencia.contains("-")) {
                String[] partesAgencia = agencia.split("-");
                agenciaNumero = partesAgencia[0];
                if (partesAgencia.length > 1) {
                    agenciaDigito = partesAgencia[1];
                }
            }
            
            String contaNumero = conta;
            String contaDigito = digitoConta;
            if (conta != null && conta.contains("-")) {
                String[] partesConta = conta.split("-");
                contaNumero = partesConta[0];
                if (partesConta.length > 1) {
                    contaDigito = partesConta[1];
                }
            }
            
            // Substitui os placeholders do novo template
            template = template.replace("{{VALOR_PAGAMENTO}}", 
                    valorLiquido != null ? currencyFormat.format(valorLiquido) : 
                    (valorBruto != null ? currencyFormat.format(valorBruto) : "R$ 0,00"));
            
            template = template.replace("{{CODIGO_EMPREENDIMENTO}}", 
                    codigoEmpreendimento != null ? codigoEmpreendimento : "Não informado");
            
            template = template.replace("{{NOME_EMPREENDIMENTO}}", 
                    nomeCondominio != null ? nomeCondominio : "Não informado");
            
            template = template.replace("{{VENCIMENTO}}", 
                    vencimento != null ? vencimento : dataAtual);
            
            template = template.replace("{{CONTA_CONTABIL}}", 
                    contaContabil != null ? contaContabil : "Não informado");
            
            template = template.replace("{{DESCRICAO_PAGAMENTO}}", 
                    descricaoPagamento != null ? descricaoPagamento : 
                    (nomePrestador != null ? "Pagamento de serviços - " + nomePrestador : "Pagamento de serviços"));
            
            template = template.replace("{{DOCUMENTO_FORNECEDOR}}", 
                    cpfPrestador != null ? formatarCPF(cpfPrestador) : "Não informado");
            
            template = template.replace("{{NOME_FORNECEDOR}}", 
                    nomePrestador != null ? nomePrestador : "Não informado");
            
            template = template.replace("{{DOCUMENTO_FAVORECIDO}}", 
                    cpfPrestador != null ? formatarCPF(cpfPrestador) : "Não informado");
            
            template = template.replace("{{NOME_FAVORECIDO}}", 
                    nomePrestador != null ? nomePrestador : "Não informado");
            
            template = template.replace("{{NUMERO_BANCO}}", 
                    nomeBanco != null ? nomeBanco : "Não informado");
            
            // Formata agência (só mostra dígito se existir)
            String agenciaFormatada = agenciaNumero != null ? agenciaNumero : "Não informado";
            if (agenciaDigito != null && !agenciaDigito.isBlank()) {
                agenciaFormatada = agenciaNumero + "-" + agenciaDigito;
            }
            template = template.replace("{{AGENCIA_FORMATADA}}", agenciaFormatada);
            
            // Formata conta (só mostra dígito se existir)
            String contaFormatada = contaNumero != null ? contaNumero : "Não informado";
            if (contaDigito != null && !contaDigito.isBlank()) {
                contaFormatada = contaNumero + "-" + contaDigito;
            }
            template = template.replace("{{CONTA_FORMATADA}}", contaFormatada);
            
            template = template.replace("{{CHAVE_PIX}}", 
                    chavePix != null ? chavePix : "Não informado");
            
            // Nome do destinatário
            template = template.replace("{{NOME_DESTINATARIO}}", 
                    nomeDestinatario != null && !nomeDestinatario.isBlank() ? nomeDestinatario : "Sistema");
            
            // Logo será adicionada como recurso inline (CID), não precisa substituir aqui
            // O template já tem <img src="cid:logo" ... />
            
            return template;
            
        } catch (IOException e) {
            System.err.println("Erro ao carregar template de email: " + e.getMessage());
            e.printStackTrace();
            // Fallback para o template inline se houver erro
            return construirCorpoEmail(nomeDestinatario, valorBruto, nomePrestador);
        }
    }

    private String formatarCPF(String cpf) {
        if (cpf == null) {
            return "";
        }
        // Remove formatação se já existir
        String cpfLimpo = cpf.replaceAll("[^0-9]", "");
        if (cpfLimpo.length() != 11) {
            return cpf;
        }
        return String.format("%s.%s.%s-%s",
                cpfLimpo.substring(0, 3),
                cpfLimpo.substring(3, 6),
                cpfLimpo.substring(6, 9),
                cpfLimpo.substring(9, 11));
    }

    private String formatarPIS(String pis) {
        if (pis == null || pis.isBlank()) {
            return "";
        }
        // Remove formatação se já existir
        String pisLimpo = pis.replaceAll("[^0-9]", "");
        if (pisLimpo.length() != 11) {
            return pis;
        }
        return String.format("%s.%s.%s-%s",
                pisLimpo.substring(0, 3),
                pisLimpo.substring(3, 6),
                pisLimpo.substring(6, 9),
                pisLimpo.substring(9, 11));
    }

    private String converterImagemParaBase64(String caminhoImagem) {
        try {
            ClassPathResource resource = new ClassPathResource(caminhoImagem);
            
            if (!resource.exists()) {
                System.err.println("Logo não encontrada no caminho: " + caminhoImagem);
                return "";
            }
            
            byte[] imageBytes = StreamUtils.copyToByteArray(resource.getInputStream());
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            
            // Detecta o tipo de imagem pelo caminho
            String mimeType = "image/png";
            if (caminhoImagem.toLowerCase().endsWith(".jpg") || caminhoImagem.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }
            
            String resultado = "data:" + mimeType + ";base64," + base64;
            System.out.println("Logo convertida para base64 com sucesso. Tamanho: " + imageBytes.length + " bytes");
            
            return resultado;
        } catch (Exception e) {
            System.err.println("Erro ao carregar imagem para email: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private String construirCorpoEmail(String nomeDestinatario, BigDecimal valorBruto, String nomePrestador) {
        String saudacao = nomeDestinatario != null && !nomeDestinatario.isBlank()
                ? "Prezado(a) " + nomeDestinatario
                : "Prezado(a)";

        String dataAtual = LocalDate.now().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("pt", "BR"))
        );

        String valorFormatado = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"))
                .format(valorBruto);

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            line-height: 1.6;
                            color: #333;
                        }
                        .container {
                            max-width: 600px;
                            margin: 0 auto;
                            padding: 20px;
                        }
                        .header {
                            background-color: #0066cc;
                            color: white;
                            padding: 20px;
                            text-align: center;
                            border-radius: 5px 5px 0 0;
                        }
                        .content {
                            background-color: #f9f9f9;
                            padding: 30px;
                            border: 1px solid #ddd;
                        }
                        .info-box {
                            background-color: #e8f4f8;
                            border-left: 4px solid #0066cc;
                            padding: 15px;
                            margin: 20px 0;
                        }
                        .footer {
                            text-align: center;
                            padding: 20px;
                            font-size: 12px;
                            color: #666;
                            border-top: 1px solid #ddd;
                        }
                        .destaque {
                            font-weight: bold;
                            color: #0066cc;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>Recibo de Pagamento - Pró-Labore</h2>
                        </div>
                        <div class="content">
                            <p>%s,</p>
                            
                            <p>Segue em anexo o recibo de pagamento de pró-labore.</p>
                            
                            <div class="info-box">
                                <p><strong>Prestador:</strong> %s</p>
                                <p><strong>Valor:</strong> <span class="destaque">%s</span></p>
                                <p><strong>Data de Emissão:</strong> %s</p>
                            </div>
                            
                            <p>O documento está anexado a este e-mail em formato PDF.</p>
                            
                            <p>Este é um e-mail automático. Por favor, não responda a esta mensagem.</p>
                            
                            <p>Atenciosamente,<br>
                            <strong>Sistema de Recibos - Precisão</strong></p>
                        </div>
                        <div class="footer">
                            <p>© %d Sistema de Recibos - Precisão. Todos os direitos reservados.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                saudacao,
                nomePrestador != null ? nomePrestador : "Não informado",
                valorFormatado,
                dataAtual,
                LocalDate.now().getYear()
        );
    }

    private String gerarNomeArquivo(String nomePrestador) {
        String dataAtual = LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd", Locale.of("pt", "BR"))
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
        
        return nomeArquivoBase + "_" + dataAtual + ".pdf";
    }
}


