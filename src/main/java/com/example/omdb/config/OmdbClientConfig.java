package com.example.omdb.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OmdbProperties.class)
public class OmdbClientConfig {


    @Bean
    public RestClient omdbRestClient(
        OmdbProperties properties
    ) {

        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();


        Duration connectTimeout =
            properties.getConnectTimeout();


        Duration readTimeout =
            properties.getReadTimeout();


        requestFactory.setConnectTimeout(connectTimeout);

        requestFactory.setReadTimeout(readTimeout);


        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }
}
