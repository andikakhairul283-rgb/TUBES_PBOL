package com.example.tubespbolkel7.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReportExport {
    public record Document(String title, String period, List<String> headers, List<List<?>> rows) { }

    private static final String SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final Charset PDF_TEXT = Charset.forName("windows-1252");
    private static final int PDF_ROWS = 21;

    public static void excel(Path target, Document document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            entry(zip, "[Content_Types].xml", """
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
            entry(zip, "_rels/.rels", relationship("officeDocument", "xl/workbook.xml"));
            entry(zip, "xl/workbook.xml", "<workbook xmlns=\"" + SHEET + "\" xmlns:r=\"" + REL
                    + "\"><sheets><sheet name=\"Laporan\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", relationship("worksheet", "worksheets/sheet1.xml"));
            entry(zip, "xl/worksheets/sheet1.xml", sheet(document));
        }
        save(target, output.toByteArray());
    }

    public static void pdf(Path target, Document document) throws IOException {
        List<byte[]> objects = new ArrayList<>();
        int pages = Math.max(1, (document.rows().size() + PDF_ROWS - 1) / PDF_ROWS);
        StringBuilder children = new StringBuilder();
        for (int page = 0; page < pages; page++) children.append(4 + page * 2).append(" 0 R ");
        objects.add(encoded("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(encoded("<< /Type /Pages /Kids [" + children + "] /Count " + pages + " >>"));
        objects.add(encoded("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        for (int page = 0; page < pages; page++) {
            int contentId = 5 + page * 2;
            objects.add(encoded("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595]"
                    + " /Resources << /Font << /F1 3 0 R >> >> /Contents " + contentId + " 0 R >>"));
            byte[] content = pageContent(document, page, pages);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.writeBytes(encoded("<< /Length " + content.length + " >>\nstream\n"));
            stream.writeBytes(content);
            stream.writeBytes(encoded("\nendstream"));
            objects.add(stream.toByteArray());
        }

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.writeBytes(encoded("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(pdf.size());
            pdf.writeBytes(encoded((index + 1) + " 0 obj\n"));
            pdf.writeBytes(objects.get(index));
            pdf.writeBytes(encoded("\nendobj\n"));
        }
        int xref = pdf.size();
        pdf.writeBytes(encoded("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n"));
        for (int offset : offsets) pdf.writeBytes(encoded(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
        pdf.writeBytes(encoded("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n"));
        save(target, pdf.toByteArray());
    }

    private static byte[] pageContent(Document document, int page, int pages) {
        StringBuilder text = new StringBuilder("0.09 0.17 0.29 rg\n");
        pdfText(text, 36, 548, 22, document.title());
        pdfText(text, 36, 525, 10, document.period());
        text.append("0.91 0.94 0.99 rg 36 477 770 23 re f\n0.09 0.17 0.29 rg\n");
        int spacing = 770 / document.headers().size();
        for (int col = 0; col < document.headers().size(); col++) {
            pdfText(text, 43 + spacing * col, 485, 9, document.headers().get(col));
        }
        int start = page * PDF_ROWS;
        for (int index = start; index < Math.min(start + PDF_ROWS, document.rows().size()); index++) {
            int y = 461 - (index - start) * 20;
            List<?> row = document.rows().get(index);
            for (int col = 0; col < row.size(); col++) {
                pdfText(text, 43 + spacing * col, y, 10, row.get(col).toString());
            }
            text.append("0.91 0.94 0.98 RG 36 ").append(y - 7).append(" m 806 ").append(y - 7).append(" l S\n");
        }
        pdfText(text, 36, 28, 9, "CheckIn.id");
        pdfText(text, 711, 28, 9, "Halaman " + (page + 1) + " / " + pages);
        return encoded(text.toString());
    }

    private static void pdfText(StringBuilder output, int x, int y, int size, String value) {
    StringBuilder clean = new StringBuilder();

    for (int index = 0; index < value.length(); index++) {
        char character = value.charAt(index);

        if (character == '\n' || character == '\r' || character == '\t') {
            clean.append(' ');
        } else if (!Character.isISOControl(character)) {
            clean.append(character);
        }
    }

    String escaped = clean.toString()
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)");

    output.append("BT /F1 ").append(size).append(" Tf ").append(x).append(' ').append(y)
            .append(" Td (").append(escaped).append(") Tj ET\n");
}

    private static byte[] encoded(String value) {
    CharsetEncoder encoder = PDF_TEXT.newEncoder();
    StringBuilder safe = new StringBuilder(value.length());

    for (int index = 0; index < value.length();) {
        int codePoint = value.codePointAt(index);
        String character = new String(Character.toChars(codePoint));

        safe.append(encoder.canEncode(character) ? character : "?");
        index += Character.charCount(codePoint);
    }

    return safe.toString().getBytes(PDF_TEXT);
}

    private static String sheet(Document document) {
        StringBuilder sheet = new StringBuilder("<worksheet xmlns=\"" + SHEET + "\"><cols><col min=\"1\" max=\""
                + document.headers().size() + "\" width=\"23\" customWidth=\"1\"/></cols><sheetData>");
        row(sheet, 1, List.of(document.title()));
        row(sheet, 2, List.of(document.period()));
        row(sheet, 4, document.headers());
        for (int index = 0; index < document.rows().size(); index++) row(sheet, index + 5, document.rows().get(index));
        return sheet.append("</sheetData></worksheet>").toString();
    }

    private static void row(StringBuilder sheet, int row, List<?> values) {
        sheet.append("<row r=\"").append(row).append("\">");
        for (int col = 0; col < values.size(); col++) {
            Object value = values.get(col);
            sheet.append("<c r=\"").append((char) ('A' + col)).append(row).append('"');
            if (value instanceof Number) {
                sheet.append("><v>").append(value).append("</v></c>");
            } else {
                sheet.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escapeXml(value.toString()))
                        .append("</t></is></c>");
            }
        }
        sheet.append("</row>");
    }

    private static String escapeXml(String value) {
    StringBuilder escaped = new StringBuilder();

    for (int index = 0; index < value.length();) {
        int codePoint = value.codePointAt(index);

        if (codePoint == '&') {
            escaped.append("&amp;");
        } else if (codePoint == '<') {
            escaped.append("&lt;");
        } else if (codePoint == '>') {
            escaped.append("&gt;");
        } else if (codePoint == '"') {
            escaped.append("&quot;");
        } else if (codePoint == '\'') {
            escaped.append("&apos;");
        } else if (codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF)) {
            escaped.appendCodePoint(codePoint);
        }

        index += Character.charCount(codePoint);
    }

    return escaped.toString();
}

    private static String relationship(String type, String target) {
        return "<Relationships xmlns=\"" + PACKAGE_REL + "\"><Relationship Id=\"rId1\" Type=\""
                + REL + "/" + type + "\" Target=\"" + target + "\"/></Relationships>";
    }

    private static void entry(ZipOutputStream zip, String path, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write((XML + contents).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void save(Path target, byte[] contents) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".checkin-report-", ".tmp");
        try {
            Files.write(temp, contents);
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
