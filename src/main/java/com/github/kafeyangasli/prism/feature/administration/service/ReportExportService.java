package com.github.kafeyangasli.prism.feature.administration.service;

import com.github.kafeyangasli.prism.feature.administration.dto.FacilityRecapRow;
import com.github.kafeyangasli.prism.feature.administration.dto.RecapResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        return RecapPdfTable.render("Periode: " + result.filter().startDate() + " s.d. " + result.filter().endDate(),
                List.of(HEADERS), result.rows().stream().map(this::cells).toList());
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
                Long.toString(row.approvedSlots()), row.capacityDisplay(),
                row.occupancyDisplay(), Long.toString(row.issueCount()));
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

}
