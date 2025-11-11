package com.precisao.recibo.controller;

import com.precisao.recibo.dto.ReciboRequest;
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

@RestController
@RequestMapping("/recibos")
public class ReciboController {

    private final PdfGeracaoService pdfGeracaoService;

    public ReciboController(PdfGeracaoService pdfGeracaoService) {
        this.pdfGeracaoService = pdfGeracaoService;
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
}

