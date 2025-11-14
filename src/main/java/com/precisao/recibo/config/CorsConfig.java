package com.precisao.recibo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        
        // TEMPORÁRIO: Permite TODAS as origens para teste
        corsConfiguration.setAllowedOriginPatterns(Arrays.asList("*"));
        
        // Permite todos os métodos HTTP
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Permite todos os headers
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));
        
        // NÃO permite credenciais quando usa * (causa erro)
        corsConfiguration.setAllowCredentials(false);
        
        // Tempo de cache da configuração CORS (1 hora)
        corsConfiguration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        
        return new CorsFilter(source);
    }
}


