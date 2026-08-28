package com.example.buddy.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.film.FilmCatalogBootstrapRunner;

@Configuration(proxyBeanMethods = false)
public class MioCatalogBootstrapConfiguration {

    @Bean
    public MioCatalogBootstrapFilter mioCatalogBootstrapFilter(
            FilmCatalogBootstrapRunner bootstrapRunner
    ) {
        return new MioCatalogBootstrapFilter(bootstrapRunner);
    }
}
