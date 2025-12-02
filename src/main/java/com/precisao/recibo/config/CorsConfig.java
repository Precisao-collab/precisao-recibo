package com.precisao.recibo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Permite todas as origens
        config.addAllowedOriginPattern("*");
        
        // Permite todos os métodos HTTP
        config.addAllowedMethod("*");
        
        // Permite todos os headers
        config.addAllowedHeader("*");
        
        // Permite credenciais (necessário para alguns casos)
        config.setAllowCredentials(false);
        
        // Cache da configuração CORS por 1 hora
        config.setMaxAge(3600L);
        
        // Aplica para todos os endpoints
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

