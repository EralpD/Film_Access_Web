package com.example.omdb.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.omdb")
public class OmdbProperties {

    private String baseUrl;

    private String apiKey;

    private Duration connectTimeout;

    private Duration readTimeout;


    public String getBaseUrl() {
        return baseUrl;
    }


    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }


    public String getApiKey() {
        return apiKey;
    }


    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }


    public Duration getConnectTimeout() {
        return connectTimeout;
    }


    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }


    public Duration getReadTimeout() {
        return readTimeout;
    }


    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
