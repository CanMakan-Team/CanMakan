package com.canmakan.backend.shared.config;

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
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}