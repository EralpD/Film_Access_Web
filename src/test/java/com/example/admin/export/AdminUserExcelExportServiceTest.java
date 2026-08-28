package com.example.admin.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.user.UserRole;

@ExtendWith(MockitoExtension.class)
class AdminUserExcelExportServiceTest {

    @Mock
    private AdminUserExportRepository exportRepository;

    private AdminUserExcelExportService exportService;

    @BeforeEach
    void setUp() {
        exportService =
                new AdminUserExcelExportService(
                        exportRepository
                );
    }

    @Test
    void shouldGenerateValidWorkbook()
            throws Exception {

        OffsetDateTime now =
                OffsetDateTime.of(
                        2026,
                        8,
                        21,
                        12,
                        30,
                        0,
                        0,
                        ZoneOffset.UTC
                );

        when(
                exportRepository.loadSummary(
                        eq("admin@example.com"),
                        any(OffsetDateTime.class)
                )
        ).thenAnswer(invocation ->
                new AdminExportSummary(
                        invocation.getArgument(1),
                        "admin@example.com",
                        1,
                        1,
                        0,
                        1,
                        1
                )
        );

        when(
                exportRepository.findUsersAfter(
                        0,
                        2_000
                )
        ).thenReturn(
                List.of(
                        new UserExportRow(
                                1,
                                "=HYPERLINK(\"https://example.com\")",
                                "user@example.com",
                                UserRole.USER,
                                true,
                                now,
                                now,
                                1,
                                now
                        )
                )
        );

        when(
                exportRepository.findUsersAfter(
                        1,
                        2_000
                )
        ).thenReturn(
                List.of()
        );

        when(
                exportRepository.findUserFilmsAfter(
                        0,
                        2_000
                )
        ).thenReturn(
                List.of(
                        new UserFilmExportRow(
                                10,
                                1,
                                "Test User",
                                "user@example.com",
                                now,
                                100,
                                "tt1375666",
                                "Inception",
                                "2010",
                                "MOVIE",
                                "Action, Sci-Fi",
                                new BigDecimal("8.8")
                        )
                )
        );

        when(
                exportRepository.findUserFilmsAfter(
                        10,
                        2_000
                )
        ).thenReturn(
                List.of()
        );

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        exportService.writeUserExport(
                outputStream,
                "admin@example.com"
        );

        byte[] workbookBytes =
                outputStream.toByteArray();

        assertThat(workbookBytes)
                .isNotEmpty();

        try (
                XSSFWorkbook workbook =
                        new XSSFWorkbook(
                                new ByteArrayInputStream(
                                        workbookBytes
                                )
                        )
        ) {
            assertThat(
                    workbook.getSheet("Summary")
            ).isNotNull();

            assertThat(
                    workbook.getSheet("Users")
            ).isNotNull();

            assertThat(
                    workbook.getSheet(
                            "User_Films"
                    )
            ).isNotNull();

            assertThat(
                    workbook
                            .getSheet("Users")
                            .getLastRowNum()
            ).isEqualTo(1);

            assertThat(
                    workbook
                            .getSheet(
                                    "User_Films"
                            )
                            .getLastRowNum()
            ).isEqualTo(1);

            var maliciousCell =
                    workbook
                            .getSheet("Users")
                            .getRow(1)
                            .getCell(1);

            assertThat(
                    maliciousCell.getCellType()
            ).isEqualTo(
                    CellType.STRING
            );

            assertThat(
                    maliciousCell.getStringCellValue()
            ).startsWith("'");

            assertThat(workbook.getSheet("Users").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("User ID");
            assertThat(workbook.getSheet("Users").getRow(1).getCell(4).getStringCellValue())
                    .isEqualTo("Yes");
        }
    }
}
