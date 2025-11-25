package com.precisao.recibo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/empreendimentos")
public class EmpreendimentoController {

    @Value("${api.empreendimento.url}")
    private String apiUrl;

    @Value("${api.empreendimento.nm_sistema}")
    private String nmSistema;

    @Value("${api.empreendimento.id_pessoafisica}")
    private String idPessoaFisica;

    @Value("${api.empreendimento.id_sessao}")
    private String idSessao;

    @Value("${api.empreendimento.id_chavedispositivo}")
    private String idChaveDispositivo;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listarEmpreendimentos() {
        try {
            String payload = gerarPayload();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("text/xml; charset=utf-8"));
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
            
            HttpEntity<String> request = new HttpEntity<>(payload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                apiUrl,
                request,
                String.class
            );
            

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
                    
        } catch (Exception e) {
            System.err.println("Erro ao buscar empreendimentos: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"erro\": \"Erro ao buscar empreendimentos: " + e.getMessage() + "\"}");
        }
    }

    private String gerarPayload() {
        return String.format(
            "<pacote>" +
            "<infoLogin>" +
            "<nm_sistema>%s</nm_sistema>" +
            "<id_pessoafisica>%s</id_pessoafisica>" +
            "<id_sessao>%s</id_sessao>" +
            "<id_chavedispositivo>%s</id_chavedispositivo>" +
            "</infoLogin>" +
            "<id_empreendimento>0</id_empreendimento>" +
            "</pacote>",
            nmSistema,
            idPessoaFisica,
            idSessao,
            idChaveDispositivo
        );
    }
}

