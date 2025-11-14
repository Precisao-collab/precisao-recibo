package com.precisao.recibo.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class HtmlToPdfService {

    public byte[] gerarPdfDeHtml(String templatePath, Map<String, String> dados) throws IOException {
        // Carrega o template HTML
        ClassPathResource resource = new ClassPathResource(templatePath);
        String htmlTemplate;
        
        try (InputStream inputStream = resource.getInputStream()) {
            htmlTemplate = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }

        // Substitui os placeholders pelos valores
        for (Map.Entry<String, String> entry : dados.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            htmlTemplate = htmlTemplate.replace(placeholder, value);
        }

        // Converte HTML para PDF
        return converterHtmlParaPdf(htmlTemplate);
    }

    private byte[] converterHtmlParaPdf(String html) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
        } catch (Exception e) {
            throw new IOException("Erro ao converter HTML para PDF", e);
        }

        return outputStream.toByteArray();
    }

    public String converterImagemParaBase64(String caminhoImagem) {
        try {
            ClassPathResource resource = new ClassPathResource(caminhoImagem);
            byte[] imageBytes = StreamUtils.copyToByteArray(resource.getInputStream());
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            
            // Detecta o tipo de imagem pelo caminho
            String mimeType = "image/png";
            if (caminhoImagem.toLowerCase().endsWith(".jpg") || caminhoImagem.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }
            
            return "data:" + mimeType + ";base64," + base64;
        } catch (Exception e) {
            System.err.println("Erro ao carregar imagem: " + e.getMessage());
            return "";
        }
    }
}


