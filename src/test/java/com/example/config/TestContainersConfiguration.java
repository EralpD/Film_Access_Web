// src/test/java/com/example/config/TestContainersConfiguration.java
package com.example.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestContainersConfiguration {
    
    @Bean
    public PostgreSQLContainer<?> postgresContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
        )
        .withDatabaseName("film_db")
        .withUsername("postgres")
        .withPassword("postgres");
        
        container.start();
        return container;
    }
}