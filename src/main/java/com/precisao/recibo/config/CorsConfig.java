package com.precisao.recibo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Permite todas as origens (para desenvolvimento e acesso via IP)
        // Em produção, restringir para origens específicas
        config.addAllowedOriginPattern("*");
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(false); // Mudado para false quando usa wildcard
        config.setMaxAge(3600L);
        config.setExposedHeaders(Arrays.asList("*"));
        
        // Aplica a configuração para todos os endpoints
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

