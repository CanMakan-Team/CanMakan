package com.canmakan.backend.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/*
 *  Config for web client
 *
 *  @author Amelia
*/
@Configuration
public class HttpClientConfig {

    @Bean
    WebClient webClient() {
        return WebClient.create();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}