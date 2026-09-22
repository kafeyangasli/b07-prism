package com.github.kafeyangasli.prism.feature.administration.service;

import com.github.kafeyangasli.prism.feature.administration.dto.FacilityRecapRow;
import com.github.kafeyangasli.prism.feature.administration.dto.RecapResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class ReportExportService {

    private static final String[] HEADERS = {
            "Kode Fasilitas", "Nama Fasilitas", "Tipe", "Lokasi",
            "Slot Disetujui", "Slot Dapat Dipesan", "Okupansi (%)", "Frekuensi Laporan"
    };

    public byte[] toCsv(RecapResult result) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, List.of(HEADERS));
        for (FacilityRecapRow row : result.rows()) {
            appendCsvRow(csv, cells(row));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toXlsx(RecapResult result) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Rekap PRISM" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                      <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                      <borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs>
                      <cellXfs count="2"><xf fontId="0" fillId="0" borderId="0" xfId="0"/><xf fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
                    </styleSheet>
                    """);
            put(zip, "xl/worksheets/sheet1.xml", worksheetXml(result));
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Gagal membuat ekspor XLSX", exception);
        }
    }

    public byte[] toPdf(RecapResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("REKAP ADMINISTRASI PRISM");
        lines.add("Periode: " + result.filter().startDate() + " s.d. " + result.filter().endDate());
        lines.add(String.join(" | ", HEADERS));
        for (FacilityRecapRow row : result.rows()) {
            lines.add(String.join(" | ", cells(row)));
        }
        if (result.rows().isEmpty()) {
            lines.add("Tidak ada data untuk filter yang dipilih.");
        }
        return simplePdf(lines);
    }

    private String worksheetXml(RecapResult result) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetData>");
        appendSheetRow(xml, List.of(HEADERS), 1, true);
        int rowNumber = 2;
        for (FacilityRecapRow row : result.rows()) {
            appendSheetRow(xml, cells(row), rowNumber++, false);
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private void appendSheetRow(StringBuilder xml, List<String> cells, int row, boolean header) {
        xml.append("<row r=\"").append(row).append("\">");
        for (int column = 0; column < cells.size(); column++) {
            String reference = columnName(column) + row;
            xml.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"");
            if (header) {
                xml.append(" s=\"1\"");
            }
            xml.append("><is><t>").append(xmlEscape(cells.get(column))).append("</t></is></c>");
        }
        xml.append("</row>");
    }

    private List<String> cells(FacilityRecapRow row) {
        return List.of(row.facilityCode(), row.facilityName(), row.facilityType(), row.location(),
                Long.toString(row.approvedSlots()), Long.toString(row.bookableSlots()),
                row.occupancyPercent().toPlainString(), Long.toString(row.issueCount()));
    }

    private void appendCsvRow(StringBuilder csv, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append('"').append(cells.get(index).replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private void put(ZipOutputStream zip, String path, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String columnName(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private byte[] simplePdf(List<String> allLines) {
        final int linesPerPage = 48;
        int pageCount = Math.max(1, (allLines.size() + linesPerPage - 1) / linesPerPage);
        int fontObject = 3 + pageCount * 2;
        List<byte[]> objects = new ArrayList<>();
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        for (int page = 0; page < pageCount; page++) {
            kids.append(3 + page * 2).append(" 0 R ");
        }
        objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>"));
        for (int page = 0; page < pageCount; page++) {
            int pageObject = 3 + page * 2;
            int contentObject = pageObject + 1;
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] "
                    + "/Resources << /Font << /F1 " + fontObject + " 0 R >> >> "
                    + "/Contents " + contentObject + " 0 R >>"));
            String content = pdfPageContent(allLines.subList(page * linesPerPage,
                    Math.min(allLines.size(), (page + 1) * linesPerPage)));
            byte[] contentBytes = bytes(content);
            objects.add(bytes("<< /Length " + contentBytes.length + " >>\nstream\n"
                    + content + "\nendstream"));
        }
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(bytes("%PDF-1.4\n"));
            List<Integer> offsets = new ArrayList<>();
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(output.size());
                output.write(bytes((index + 1) + " 0 obj\n"));
                output.write(objects.get(index));
                output.write(bytes("\nendobj\n"));
            }
            int xref = output.size();
            output.write(bytes("xref\n0 " + (objects.size() + 1) + "\n"));
            output.write(bytes("0000000000 65535 f \n"));
            for (Integer offset : offsets) {
                output.write(bytes(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
            }
            output.write(bytes("trailer\n<< /Size " + (objects.size() + 1)
                    + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF"));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Gagal membuat ekspor PDF", exception);
        }
    }

    private String pdfPageContent(List<String> lines) {
        StringBuilder content = new StringBuilder("BT /F1 8 Tf 30 560 Td");
        for (String line : lines) {
            content.append(" (").append(pdfEscape(ascii(line))).append(") Tj 0 -11 Td");
        }
        return content.append(" ET").toString();
    }

    private String ascii(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
