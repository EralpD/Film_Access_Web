package com.example.buddy.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.film.FilmCatalogBootstrapRunner;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MioCatalogBootstrapFilter extends OncePerRequestFilter {

    public static final String EMPTY_RESULT_ATTRIBUTE =
            MioCatalogBootstrapFilter.class.getName() + ".emptyResult";

    private static final Logger log =
            LoggerFactory.getLogger(MioCatalogBootstrapFilter.class);

    private final FilmCatalogBootstrapRunner bootstrapRunner;

    public MioCatalogBootstrapFilter(FilmCatalogBootstrapRunner bootstrapRunner) {
        this.bootstrapRunner = bootstrapRunner;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(request.getContextPath() + "/search").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);

        if (Boolean.TRUE.equals(request.getAttribute(EMPTY_RESULT_ATTRIBUTE))
                && response.getStatus() >= 200 && response.getStatus() < 300) {
            // Finish rendering and send Mio's reply before starting any import work.
            response.flushBuffer();
            try {
                bootstrapRunner.start();
            } catch (RuntimeException exception) {
                // The reply has already been sent; background failures must not replace it.
                log.warn("Mio catalog bootstrap could not be scheduled.", exception);
            }
        }
    }
}
