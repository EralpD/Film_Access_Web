package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.example.user.security.AccountAuthenticationFailureHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public AccountAuthenticationFailureHandler accountAuthenticationFailureHandler() {
        return new AccountAuthenticationFailureHandler();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AccountAuthenticationFailureHandler authenticationFailureHandler
    ) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/register",
                    "/login",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .failureHandler(authenticationFailureHandler)
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
            )

            .headers(headers -> headers
                .frameOptions(frame -> frame.deny()) // X-Frame-Options: DENY
            );

        return http.build();
    }
}
