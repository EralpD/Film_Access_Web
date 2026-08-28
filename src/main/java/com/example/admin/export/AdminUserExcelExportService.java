package com.example.admin.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdminUserExcelExportService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AdminUserExcelExportService.class
            );

    private static final int QUERY_BATCH_SIZE = 2_000;
    private static final int ROW_WINDOW_SIZE = 100;
    private static final int MAX_DATA_ROWS_PER_SHEET =
            1_000_000;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final String[] USER_HEADERS = {
            "User ID",
            "Display Name",
            "Email",
            "Role",
            "Active?",
            "Registration Date",
            "Last Updated",
            "Archived Film Count",
            "Last Added to Archive"
    };

    private static final int[] USER_WIDTHS = {
            16,
            28,
            36,
            14,
            14,
            28,
            28,
            20,
            30
    };

    private static final String[] USER_FILM_HEADERS = {
            "Entry ID",
            "User ID",
            "Display Name",
            "Email",
            "Added to Archive",
            "Film ID",
            "IMDb ID",
            "Film Title",
            "Year",
            "Type",
            "Genres",
            "IMDb Rating"
    };

    private static final int[] USER_FILM_WIDTHS = {
            16,
            16,
            28,
            36,
            28,
            16,
            18,
            42,
            14,
            16,
            36,
            16
    };

    private final AdminUserExportRepository exportRepository;

    public AdminUserExcelExportService(
            AdminUserExportRepository exportRepository
    ) {
        this.exportRepository = exportRepository;
    }

    public AdminExportSummary getSummary(
            String requestingAdminEmail
    ) {
        validateAdminEmail(requestingAdminEmail);

        return exportRepository.loadSummary(
                requestingAdminEmail,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public void writeUserExport(
            OutputStream outputStream,
            String requestingAdminEmail
    ) throws IOException {
        Objects.requireNonNull(
                outputStream,
                "Output stream cannot be null."
        );

        validateAdminEmail(requestingAdminEmail);

        long startedAtNanos =
                System.nanoTime();

        OffsetDateTime generatedAt =
                OffsetDateTime.now(ZoneOffset.UTC);

        AdminExportSummary summary =
                exportRepository.loadSummary(
                        requestingAdminEmail,
                        generatedAt
                );

        SXSSFWorkbook workbook =
                new SXSSFWorkbook(
                        ROW_WINDOW_SIZE
                );

        workbook.setCompressTempFiles(true);

        long exportedUsers = 0;
        long exportedUserFilms = 0;

        try (workbook) {
            Styles styles =
                    createStyles(workbook);

            writeSummarySheet(
                    workbook,
                    summary,
                    styles
            );

            exportedUsers =
                    writeUsers(
                            workbook,
                            styles
                    );

            exportedUserFilms =
                    writeUserFilms(
                            workbook,
                            styles
                    );

            workbook.write(outputStream);
            outputStream.flush();

        } finally {
            workbook.dispose();
        }

        long elapsedMillis =
                (System.nanoTime() - startedAtNanos)
                        / 1_000_000;

        log.info(
                "Admin user export completed. "
                        + "Admin: {}, users: {}, "
                        + "user film relations: {}, "
                        + "elapsedMillis: {}",
                requestingAdminEmail,
                exportedUsers,
                exportedUserFilms,
                elapsedMillis
        );
    }

    private long writeUsers(
            SXSSFWorkbook workbook,
            Styles styles
    ) {
        long lastSeenUserId = 0;
        long exportedRows = 0;

        int sheetNumber = 1;

        TabularSheet target =
                createTabularSheet(
                        workbook,
                        sheetName(
                                "Users",
                                sheetNumber
                        ),
                        USER_HEADERS,
                        USER_WIDTHS,
                        styles.header()
                );

        while (true) {
            List<UserExportRow> users =
                    exportRepository.findUsersAfter(
                            lastSeenUserId,
                            QUERY_BATCH_SIZE
                    );

            if (users.isEmpty()) {
                break;
            }

            for (UserExportRow user : users) {
                if (target.isFull()) {
                    target.finish();

                    sheetNumber++;

                    target = createTabularSheet(
                            workbook,
                            sheetName(
                                    "Users",
                                    sheetNumber
                            ),
                            USER_HEADERS,
                            USER_WIDTHS,
                            styles.header()
                    );
                }

                Row row = target.nextRow();

                writeId(
                        row,
                        0,
                        user.userId()
                );

                writeText(
                        row,
                        1,
                        user.displayName()
                );

                writeText(
                        row,
                        2,
                        user.email()
                );

                writeText(
                        row,
                        3,
                        user.role() == null
                                ? null
                                : user.role().name()
                );

                writeText(
                        row,
                        4,
                        user.enabled()
                                ? "Yes"
                                : "No"
                );

                writeDate(
                        row,
                        5,
                        user.createdAt()
                );

                writeDate(
                        row,
                        6,
                        user.updatedAt()
                );

                writeNumber(
                        row,
                        7,
                        user.archiveFilmCount()
                );

                writeDate(
                        row,
                        8,
                        user.lastArchiveAddition()
                );

                lastSeenUserId =
                        user.userId();

                exportedRows++;
            }
        }

        target.finish();

        return exportedRows;
    }

    private long writeUserFilms(
            SXSSFWorkbook workbook,
            Styles styles
    ) {
        long lastSeenUserFilmId = 0;
        long exportedRows = 0;

        int sheetNumber = 1;

        TabularSheet target =
                createTabularSheet(
                        workbook,
                        sheetName(
                                "User_Films",
                                sheetNumber
                        ),
                        USER_FILM_HEADERS,
                        USER_FILM_WIDTHS,
                        styles.header()
                );

        while (true) {
            List<UserFilmExportRow> userFilms =
                    exportRepository
                            .findUserFilmsAfter(
                                    lastSeenUserFilmId,
                                    QUERY_BATCH_SIZE
                            );

            if (userFilms.isEmpty()) {
                break;
            }

            for (UserFilmExportRow userFilm : userFilms) {
                if (target.isFull()) {
                    target.finish();

                    sheetNumber++;

                    target = createTabularSheet(
                            workbook,
                            sheetName(
                                    "User_Films",
                                    sheetNumber
                            ),
                            USER_FILM_HEADERS,
                            USER_FILM_WIDTHS,
                            styles.header()
                    );
                }

                Row row = target.nextRow();

                writeId(
                        row,
                        0,
                        userFilm.userFilmId()
                );

                writeId(
                        row,
                        1,
                        userFilm.userId()
                );

                writeText(
                        row,
                        2,
                        userFilm.displayName()
                );

                writeText(
                        row,
                        3,
                        userFilm.email()
                );

                writeDate(
                        row,
                        4,
                        userFilm.addedAt()
                );

                writeId(
                        row,
                        5,
                        userFilm.filmId()
                );

                writeText(
                        row,
                        6,
                        userFilm.imdbId()
                );

                writeText(
                        row,
                        7,
                        userFilm.title()
                );

                writeText(
                        row,
                        8,
                        userFilm.yearText()
                );

                writeText(
                        row,
                        9,
                        userFilm.type()
                );

                writeText(
                        row,
                        10,
                        userFilm.genresText()
                );

                writeDecimal(
                        row,
                        11,
                        userFilm.imdbRating()
                );

                lastSeenUserFilmId =
                        userFilm.userFilmId();

                exportedRows++;
            }
        }

        target.finish();

        return exportedRows;
    }

    private void writeSummarySheet(
            SXSSFWorkbook workbook,
            AdminExportSummary summary,
            Styles styles
    ) {
        SXSSFSheet sheet =
                workbook.createSheet("Summary");

        sheet.setColumnWidth(
                0,
                34 * 256
        );

        sheet.setColumnWidth(
                1,
                42 * 256
        );

        Row titleRow =
                sheet.createRow(0);

        titleRow.setHeightInPoints(28);

        Cell titleCell =
                titleRow.createCell(
                        0,
                        CellType.STRING
                );

        titleCell.setCellValue(
                "User and Archive Export Summary"
        );

        titleCell.setCellStyle(
                styles.title()
        );

        sheet.addMergedRegion(
                new CellRangeAddress(
                        0,
                        0,
                        0,
                        1
                )
        );

        int rowNumber = 2;

        writeSummaryValue(
                sheet,
                rowNumber++,
                "Generated At",
                formatDate(summary.generatedAt()),
                styles
        );

        writeSummaryValue(
                sheet,
                rowNumber++,
                "Generated By",
                summary.generatedBy(),
                styles
        );

        writeSummaryValue(
                sheet,
                rowNumber++,
                "Total Users",
                Long.toString(
                        summary.totalUsers()
                ),
                styles
        );

        writeSummaryValue(
                sheet,
                rowNumber++,
                "Active Users",
                Long.toString(
                        summary.enabledUsers()
                ),
                styles
        );

        writeSummaryValue(
                sheet,
                rowNumber++,
                "Inactive Users",
                Long.toString(
                        summary.disabledUsers()
                ),
                styles
        );

        writeSummaryValue(
                sheet,
                rowNumber++,
                "Total Archive Entries",
                Long.toString(
                        summary.totalArchiveRelations()
                ),
                styles
        );

        writeSummaryValue(
                sheet,
                rowNumber,
                "Unique Archived Films",
                Long.toString(
                        summary.distinctArchivedFilms()
                ),
                styles
        );
    }

    private void writeSummaryValue(
            SXSSFSheet sheet,
            int rowNumber,
            String label,
            String value,
            Styles styles
    ) {
        Row row =
                sheet.createRow(rowNumber);

        Cell labelCell =
                row.createCell(
                        0,
                        CellType.STRING
                );

        labelCell.setCellValue(
                label
        );

        labelCell.setCellStyle(
                styles.summaryLabel()
        );

        Cell valueCell =
                row.createCell(
                        1,
                        CellType.STRING
                );

        valueCell.setCellValue(
                safeText(value)
        );
    }

    private TabularSheet createTabularSheet(
            SXSSFWorkbook workbook,
            String name,
            String[] headers,
            int[] widths,
            CellStyle headerStyle
    ) {
        SXSSFSheet sheet =
                workbook.createSheet(name);

        sheet.createFreezePane(
                0,
                1
        );

        Row headerRow =
                sheet.createRow(0);

        headerRow.setHeightInPoints(24);

        for (int index = 0;
                index < headers.length;
                index++) {

            Cell cell =
                    headerRow.createCell(
                            index,
                            CellType.STRING
                    );

            cell.setCellValue(
                    headers[index]
            );

            cell.setCellStyle(
                    headerStyle
            );

            sheet.setColumnWidth(
                    index,
                    widths[index] * 256
            );
        }

        return new TabularSheet(
                sheet,
                headers.length - 1
        );
    }

    private Styles createStyles(
            SXSSFWorkbook workbook
    ) {
        Font titleFont =
                workbook.createFont();

        titleFont.setBold(true);
        titleFont.setColor(
                IndexedColors.WHITE.getIndex()
        );

        titleFont.setFontHeightInPoints(
                (short) 15
        );

        CellStyle titleStyle =
                workbook.createCellStyle();

        titleStyle.setFont(titleFont);

        titleStyle.setFillForegroundColor(
                IndexedColors.DARK_BLUE.getIndex()
        );

        titleStyle.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        titleStyle.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        Font headerFont =
                workbook.createFont();

        headerFont.setBold(true);
        headerFont.setColor(
                IndexedColors.WHITE.getIndex()
        );

        CellStyle headerStyle =
                workbook.createCellStyle();

        headerStyle.setFont(headerFont);

        headerStyle.setFillForegroundColor(
                IndexedColors.INDIGO.getIndex()
        );

        headerStyle.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        headerStyle.setVerticalAlignment(
                VerticalAlignment.CENTER
        );

        Font summaryLabelFont =
                workbook.createFont();

        summaryLabelFont.setBold(true);

        CellStyle summaryLabelStyle =
                workbook.createCellStyle();

        summaryLabelStyle.setFont(
                summaryLabelFont
        );

        summaryLabelStyle.setFillForegroundColor(
                IndexedColors.LIGHT_CORNFLOWER_BLUE
                        .getIndex()
        );

        summaryLabelStyle.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        return new Styles(
                titleStyle,
                headerStyle,
                summaryLabelStyle
        );
    }

    private void writeText(
            Row row,
            int column,
            String value
    ) {
        Cell cell =
                row.createCell(
                        column,
                        CellType.STRING
                );

        cell.setCellValue(
                safeText(value)
        );
    }

    private void writeId(
            Row row,
            int column,
            long value
    ) {
        /*
         * BIGINT değerlerini Excel'in kayan nokta hassasiyetine
         * kaybetmemek için metin hücresi olarak yazıyoruz.
         */
        writeText(
                row,
                column,
                Long.toString(value)
        );
    }

    private void writeNumber(
            Row row,
            int column,
            long value
    ) {
        Cell cell =
                row.createCell(
                        column,
                        CellType.NUMERIC
                );

        cell.setCellValue(value);
    }

    private void writeDecimal(
            Row row,
            int column,
            BigDecimal value
    ) {
        if (value == null) {
            writeText(
                    row,
                    column,
                    null
            );

            return;
        }

        Cell cell =
                row.createCell(
                        column,
                        CellType.NUMERIC
                );

        cell.setCellValue(
                value.doubleValue()
        );
    }

    private void writeDate(
            Row row,
            int column,
            OffsetDateTime value
    ) {
        writeText(
                row,
                column,
                formatDate(value)
        );
    }

    private String formatDate(
            OffsetDateTime value
    ) {
        if (value == null) {
            return "";
        }

        return DATE_FORMATTER.format(value);
    }

    private String safeText(
            String value
    ) {
        if (value == null) {
            return "";
        }

        StringBuilder result =
                new StringBuilder(
                        value.length()
                );

        for (int index = 0;
                index < value.length();
                index++) {

            char character =
                    value.charAt(index);

            if (character == '\r'
                    || character == '\n'
                    || character == '\t') {

                result.append(' ');
                continue;
            }

            if (!Character.isISOControl(character)) {
                result.append(character);
            }
        }

        String sanitized =
                result.toString();

        int firstVisibleCharacter = 0;

        while (firstVisibleCharacter
                < sanitized.length()
                && Character.isWhitespace(
                        sanitized.charAt(
                                firstVisibleCharacter
                        )
                )) {

            firstVisibleCharacter++;
        }

        if (firstVisibleCharacter
                < sanitized.length()) {

            char first =
                    sanitized.charAt(
                            firstVisibleCharacter
                    );

            if (first == '='
                    || first == '+'
                    || first == '-'
                    || first == '@') {

                return "'" + sanitized;
            }
        }

        return sanitized;
    }

    private String sheetName(
            String baseName,
            int sheetNumber
    ) {
        if (sheetNumber == 1) {
            return baseName;
        }

        return baseName
                + "_"
                + sheetNumber;
    }

    private void validateAdminEmail(
            String adminEmail
    ) {
        if (adminEmail == null
                || adminEmail.isBlank()) {

            throw new IllegalArgumentException(
                    "Requesting admin email cannot be empty."
            );
        }
    }

    private record Styles(
            CellStyle title,
            CellStyle header,
            CellStyle summaryLabel
    ) {
    }

    private static final class TabularSheet {

        private final SXSSFSheet sheet;
        private final int lastColumn;

        private int nextRowIndex = 1;
        private int dataRows;
        private boolean finished;

        private TabularSheet(
                SXSSFSheet sheet,
                int lastColumn
        ) {
            this.sheet = sheet;
            this.lastColumn = lastColumn;
        }

        private Row nextRow() {
            if (isFull()) {
                throw new IllegalStateException(
                        "Excel sheet row limit reached."
                );
            }

            Row row =
                    sheet.createRow(
                            nextRowIndex
                    );

            nextRowIndex++;
            dataRows++;

            return row;
        }

        private boolean isFull() {
            return dataRows
                    >= MAX_DATA_ROWS_PER_SHEET;
        }

        private void finish() {
            if (finished) {
                return;
            }

            sheet.setAutoFilter(
                    new CellRangeAddress(
                            0,
                            Math.max(
                                    0,
                                    nextRowIndex - 1
                            ),
                            0,
                            lastColumn
                    )
            );

            finished = true;
        }
    }
}