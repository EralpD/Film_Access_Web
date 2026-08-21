package com.example.admin.controller;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.admin.export.AdminExportSummary;
import com.example.admin.export.AdminUserExcelExportService;
import com.example.config.SecurityConfig;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserExcelExportService exportService;

    @Test
    void shouldRedirectAnonymousUserToLogin()
            throws Exception {

        mockMvc.perform(
                get("/admin/exports/users.xlsx")
        )
        .andExpect(
                status().is3xxRedirection()
        );
    }

    @Test
    void shouldRejectNormalUser()
            throws Exception {

        mockMvc.perform(
                get("/admin/exports/users.xlsx")
                        .with(
                                user("user@example.com")
                                        .roles("USER")
                        )
        )
        .andExpect(
                status().isForbidden()
        );
    }

    @Test
    void shouldAllowAdminToDownloadExcel()
            throws Exception {

        byte[] expectedBody = {
                0x50,
                0x4B,
                0x03,
                0x04
        };

        doAnswer(invocation -> {
            OutputStream outputStream =
                    invocation.getArgument(0);

            outputStream.write(
                    expectedBody
            );

            return null;
        })
        .when(exportService)
        .writeUserExport(
                any(OutputStream.class),
                eq("admin@example.com")
        );

        MvcResult initialResult =
                mockMvc.perform(
                        get("/admin/exports/users.xlsx")
                                .with(
                                        user(
                                                "admin@example.com"
                                        )
                                        .roles("ADMIN")
                                )
                )
                .andExpect(
                        request().asyncStarted()
                )
                .andReturn();

        mockMvc.perform(
                asyncDispatch(initialResult)
        )
        .andExpect(
                status().isOk()
        )
        .andExpect(
                content().contentType(
                        "application/vnd.openxmlformats-officedocument."
                                + "spreadsheetml.sheet"
                )
        )
        .andExpect(
                header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate"
                )
        )
        .andExpect(
                header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString(
                                "attachment"
                        )
                )
        )
        .andExpect(
                content().bytes(
                        expectedBody
                )
        );
    }

    @Test
    void shouldAllowAdminToOpenDashboard()
            throws Exception {

        when(
                exportService.getSummary(
                        "admin@example.com"
                )
        ).thenReturn(
                new AdminExportSummary(
                        OffsetDateTime.of(
                                2026,
                                8,
                                21,
                                12,
                                0,
                                0,
                                0,
                                ZoneOffset.UTC
                        ),
                        "admin@example.com",
                        10,
                        9,
                        1,
                        25,
                        18
                )
        );

        mockMvc.perform(
                get("/admin")
                        .with(
                                user(
                                        "admin@example.com"
                                )
                                .roles("ADMIN")
                        )
        )
        .andExpect(
                status().isOk()
        );
    }
}