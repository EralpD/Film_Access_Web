package com.example.buddy.web;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.film.FilmCatalogBootstrapRunner;

import jakarta.servlet.ServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MioCatalogBootstrapFilterTest {

    @Mock
    FilmCatalogBootstrapRunner runner;

    MioCatalogBootstrapFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new MioCatalogBootstrapFilter(runner);
        request = new MockHttpServletRequest("POST", "/search");
        response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
    }

    @Test
    void sendsCompleteReplyBeforeStartingImport() throws Exception {
        doAnswer(invocation -> {
            assertThat(response.isCommitted()).isTrue();
            assertThat(response.getContentAsString()).isEqualTo("<p>Film bulamadım.</p>");
            return true;
        }).when(runner).start();

        filter.doFilter(request, response, (req, res) -> {
            verifyNoInteractions(runner);
            req.setAttribute(MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE, true);
            res.getWriter().write("<p>Film bulamadım.</p>");
            verifyNoInteractions(runner);
        });

        verify(runner).start();
    }

    @Test
    void doesNotImportWhenRenderingFails() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE, true);
            throw new ServletException("Render failed");
        })).isInstanceOf(ServletException.class);

        verifyNoInteractions(runner);
    }

    @Test
    void doesNotImportWhenSendingReplyFails() {
        var brokenResponse = new jakarta.servlet.http.HttpServletResponseWrapper(response) {
            @Override
            public void flushBuffer() throws IOException {
                throw new IOException("Disconnected");
            }
        };
        request.setAttribute(MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE, true);

        assertThatThrownBy(() -> filter.doFilter(request, brokenResponse, (req, res) -> {}))
                .isInstanceOf(IOException.class);
        verifyNoInteractions(runner);
    }

    @Test
    void doesNotImportForErrorResponse() throws Exception {
        request.setAttribute(MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE, true);

        filter.doFilter(request, response, (req, res) -> response.setStatus(500));

        verifyNoInteractions(runner);
    }

    @Test
    void keepsReplyIntactWhenSchedulingFails() throws Exception {
        when(runner.start()).thenThrow(new IllegalStateException("Executor unavailable"));
        request.setAttribute(MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE, true);

        filter.doFilter(request, response, (req, res) -> res.getWriter().write("No films."));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("No films.");
    }

    @Test
    void supportsApplicationContextPath() throws Exception {
        request.setContextPath("/films");
        request.setRequestURI("/films/search");
        request.setAttribute(MioCatalogBootstrapFilter.EMPTY_RESULT_ATTRIBUTE, true);

        filter.doFilter(request, response, (req, res) -> {});

        verify(runner).start();
    }
}
