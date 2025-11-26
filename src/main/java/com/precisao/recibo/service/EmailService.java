package com.precisao.recibo.service;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

@Service
public class EmailService {

    private final SesClient sesClient;
    private final String emailRemetente;
    private final String nomeRemetente;

    public EmailService(
            @Value("${SPRING_MAIL_USERNAME:AKIA4GR4PWO4TTRUY6LA}") String awsAccessKey,
            @Value("${SPRING_MAIL_PASSWORD:I/u1sY5wU8cxYxDDL1EjTKO6H7gotbVGjkgMWIwd}") String awsSecretKey,
            @Value("${app.email.remetente}") String emailRemetente,
            @Value("${app.email.nome-remetente}") String nomeRemetente) {
        
        this.emailRemetente = emailRemetente;
        this.nomeRemetente = nomeRemetente;
        
        // Cria cliente AWS SES usando credenciais
        // Nota: Se forem credenciais SMTP, pode não funcionar. Nesse caso, será necessário criar Access Keys IAM
        System.out.println("Configurando AWS SES SDK com Access Key: " + awsAccessKey.substring(0, Math.min(10, awsAccessKey.length())) + "...");
        
        try {
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
            this.sesClient = SesClient.builder()
                    .region(Region.US_EAST_2) // Região do SES configurada
                    .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                    .build();
            System.out.println("Cliente AWS SES configurado com sucesso!");
        } catch (Exception e) {
            System.err.println("Erro ao configurar cliente AWS SES: " + e.getMessage());
            throw new RuntimeException("Erro ao configurar AWS SES SDK. Verifique se as credenciais são Access Keys IAM válidas.", e);
        }
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

        // Email sempre enviado para central de pagamentos
        String emailCentralPagamentos = "centraldepagamentos@precisaoadm.com.br";
        
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

        try {
            System.out.println("Tentando enviar email principal para central de pagamentos usando AWS SES SDK...");
            
            // Cria mensagem MIME - email principal vai apenas para central de pagamentos (sem CC)
            MimeMessage message = criarMensagemComAnexo(
                    emailCentralPagamentos,
                    null, // Sem CC no email principal
                    assunto,
                    corpoEmail,
                    nomeArquivo,
                    pdfRecibo
            );
            
            // Envia via AWS SES SDK
            enviarViaSesSdk(message);
            System.out.println("Email principal enviado com sucesso para central de pagamentos!");
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar email principal: " + e.getMessage());
            e.printStackTrace();
            throw new MessagingException("Erro ao enviar email: " + e.getMessage(), e);
        }
        
        // Envia email separado para o gerente (cópia)
        if (emailDestinatario != null && !emailDestinatario.isBlank() && !emailDestinatario.equals(emailCentralPagamentos)) {
            try {
                System.out.println("Tentando enviar email de cópia para o gerente: " + emailDestinatario);
                
                MimeMessage messageCopia = criarMensagemComAnexo(
                        emailDestinatario,
                        null,
                        assunto + " - Cópia",
                        "Você está recebendo uma cópia do recibo solicitado.\n\n" + corpoEmail,
                        nomeArquivo,
                        pdfRecibo
                );
                
                enviarViaSesSdk(messageCopia);
                System.out.println("Email de cópia enviado com sucesso para o gerente!");
            } catch (Exception e) {
                System.err.println("Erro ao enviar cópia do email para " + emailDestinatario + ": " + e.getMessage());
                e.printStackTrace();
                // Não re-lança a exceção aqui, pois o email principal já foi enviado
            }
        }
    }

    @Async("emailExecutor")
    public void enviarReciboEmailComMultiplosAnexosAsync(
            String emailDestinatario,
            String nomeDestinatario,
            String assunto,
            java.util.Map<String, byte[]> pdfsRecibos,
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
            enviarReciboEmailComMultiplosAnexos(
                    emailDestinatario,
                    nomeDestinatario,
                    assunto,
                    pdfsRecibos,
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
        } catch (MessagingException e) {
            System.err.println("ERRO ao enviar email de forma assíncrona: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void enviarReciboEmailComMultiplosAnexos(
            String emailDestinatario,
            String nomeDestinatario,
            String assunto,
            java.util.Map<String, byte[]> pdfsRecibos,
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

        // Email sempre enviado para central de pagamentos
        String emailCentralPagamentos = "centraldepagamentos@precisaoadm.com.br";
        
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

        try {
            System.out.println("Tentando enviar email com múltiplos anexos para central de pagamentos usando AWS SES SDK...");
            
            // Cria mensagem MIME com múltiplos anexos - email principal vai apenas para central de pagamentos (sem CC)
            MimeMessage message = criarMensagemComMultiplosAnexos(
                    emailCentralPagamentos,
                    null, // Sem CC no email principal
                    assunto,
                    corpoEmail,
                    pdfsRecibos
            );
            
            // Envia via AWS SES SDK
            enviarViaSesSdk(message);
            System.out.println("Email com múltiplos anexos enviado com sucesso para central de pagamentos!");
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar email principal: " + e.getMessage());
            e.printStackTrace();
            throw new MessagingException("Erro ao enviar email: " + e.getMessage(), e);
        }
        
        // Envia email separado para o gerente (cópia)
        if (emailDestinatario != null && !emailDestinatario.isBlank() && !emailDestinatario.equals(emailCentralPagamentos)) {
            try {
                System.out.println("Tentando enviar email de cópia para o gerente: " + emailDestinatario);
                
                MimeMessage messageCopia = criarMensagemComMultiplosAnexos(
                        emailDestinatario,
                        null,
                        assunto + " - Cópia",
                        "Você está recebendo uma cópia dos recibos solicitados.\n\n" + corpoEmail,
                        pdfsRecibos
                );
                
                enviarViaSesSdk(messageCopia);
                System.out.println("Email de cópia enviado com sucesso para o gerente!");
            } catch (Exception e) {
                System.err.println("Erro ao enviar cópia do email para " + emailDestinatario + ": " + e.getMessage());
                e.printStackTrace();
                // Não re-lança a exceção aqui, pois o email principal já foi enviado
            }
        }
    }

    private MimeMessage criarMensagemComAnexo(
            String destinatario,
            String cc,
            String assunto,
            String corpoHtml,
            String nomeArquivo,
            byte[] anexo) throws MessagingException {
        
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        
        try {
            message.setFrom(new InternetAddress(emailRemetente, nomeRemetente, "UTF-8"));
            message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(destinatario));
            
            if (cc != null && !cc.isBlank()) {
                message.setRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }
            
            message.setSubject(assunto, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // Fallback sem charset se UTF-8 não for suportado
            message.setFrom(new InternetAddress(emailRemetente));
            message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(destinatario));
            
            if (cc != null && !cc.isBlank()) {
                message.setRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }
            
            message.setSubject(assunto);
        }
        
        // Cria multipart para HTML + anexo
        MimeMultipart multipart = new MimeMultipart("mixed");
        
        // Parte HTML
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(corpoHtml, "text/html; charset=UTF-8");
        multipart.addBodyPart(htmlPart);
        
        // Parte anexo
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setFileName(nomeArquivo);
        attachmentPart.setContent(anexo, "application/pdf");
        attachmentPart.setDisposition(jakarta.mail.Part.ATTACHMENT);
        multipart.addBodyPart(attachmentPart);
        
        message.setContent(multipart);
        return message;
    }

    private MimeMessage criarMensagemComMultiplosAnexos(
            String destinatario,
            String cc,
            String assunto,
            String corpoHtml,
            java.util.Map<String, byte[]> anexos) throws MessagingException {
        
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        
        try {
            message.setFrom(new InternetAddress(emailRemetente, nomeRemetente, "UTF-8"));
            message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(destinatario));
            
            if (cc != null && !cc.isBlank()) {
                message.setRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }
            
            message.setSubject(assunto, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            message.setFrom(new InternetAddress(emailRemetente));
            message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(destinatario));
            
            if (cc != null && !cc.isBlank()) {
                message.setRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }
            
            message.setSubject(assunto);
        }
        
        // Cria multipart para HTML + múltiplos anexos
        MimeMultipart multipart = new MimeMultipart("mixed");
        
        // Parte HTML
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(corpoHtml, "text/html; charset=UTF-8");
        multipart.addBodyPart(htmlPart);
        
        // Adiciona todos os anexos
        for (java.util.Map.Entry<String, byte[]> anexo : anexos.entrySet()) {
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setFileName(anexo.getKey());
            attachmentPart.setContent(anexo.getValue(), "application/pdf");
            attachmentPart.setDisposition(jakarta.mail.Part.ATTACHMENT);
            multipart.addBodyPart(attachmentPart);
        }
        
        message.setContent(multipart);
        return message;
    }

    private void enviarViaSesSdk(MimeMessage message) throws MessagingException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            message.writeTo(outputStream);
            
            RawMessage rawMessage = RawMessage.builder()
                    .data(SdkBytes.fromByteArray(outputStream.toByteArray()))
                    .build();
            
            SendRawEmailRequest rawEmailRequest = SendRawEmailRequest.builder()
                    .rawMessage(rawMessage)
                    .build();
            
            sesClient.sendRawEmail(rawEmailRequest);
            
        } catch (jakarta.mail.MessagingException e) {
            throw new MessagingException("Erro ao criar mensagem MIME: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MessagingException("Erro ao enviar via AWS SES SDK: " + e.getMessage(), e);
        }
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
            
            template = template.replace("{{AGENCIA}}", 
                    agenciaNumero != null ? agenciaNumero : "Não informado");
            
            template = template.replace("{{DIGITO_AGENCIA}}", 
                    agenciaDigito != null && !agenciaDigito.isBlank() ? agenciaDigito : "Não informado");
            
            template = template.replace("{{NUMERO_CONTA}}", 
                    contaNumero != null ? contaNumero : "Não informado");
            
            template = template.replace("{{DIGITO_CONTA}}", 
                    contaDigito != null && !contaDigito.isBlank() ? contaDigito : "Não informado");
            
            template = template.replace("{{CHAVE_PIX}}", 
                    chavePix != null ? chavePix : "Não informado");
            
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
