    package com.example.admin.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.admin.export.AdminExportSummary;
import com.example.admin.export.AdminUserExcelExportService;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument."
                            + "spreadsheetml.sheet"
            );

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HHmmss"
            );

    private final AdminUserExcelExportService excelExportService;

    public AdminController(
            AdminUserExcelExportService excelExportService
    ) {
        this.excelExportService = excelExportService;
    }

    @GetMapping
    public String dashboard(
            Principal principal,
            Model model
    ) {
        AdminExportSummary summary =
                excelExportService.getSummary(
                        principal.getName()
                );

        model.addAttribute(
                "summary",
                summary
        );

        return "admin/dashboard";
    }

    @GetMapping(
            value = "/exports/users.xlsx",
            produces = "application/vnd.openxmlformats-officedocument."
                    + "spreadsheetml.sheet"
    )
    public ResponseEntity<StreamingResponseBody>
            downloadUserExport(
                    Principal principal
            ) {

        String adminEmail =
                principal.getName();

        String fileName =
                "kullanici-arsiv-"
                        + FILE_DATE_FORMAT.format(
                                LocalDateTime.now()
                        )
                        + ".xlsx";

        StreamingResponseBody responseBody =
                outputStream ->
                        excelExportService
                                .writeUserExport(
                                        outputStream,
                                        adminEmail
                                );

        ContentDisposition disposition =
                ContentDisposition
                        .attachment()
                        .filename(
                                fileName,
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(
                        XLSX_MEDIA_TYPE
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate"
                )
                .header(
                        HttpHeaders.PRAGMA,
                        "no-cache"
                )
                .header(
                        HttpHeaders.EXPIRES,
                        "0"
                )
                .body(responseBody);
    }
}