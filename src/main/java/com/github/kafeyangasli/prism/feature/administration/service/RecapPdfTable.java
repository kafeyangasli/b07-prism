package com.github.kafeyangasli.prism.feature.administration.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.io.*;
import java.util.*;

/** Bounded landscape A4 table. Oversize rows continue line-by-line on subsequent pages. */
final class RecapPdfTable {
    private static final float MARGIN = 36, SIZE = 8, LEADING = 12, PADDING = 5;
    private static final float[] WIDTHS = {72, 158, 85, 145, 70, 80, 72, 88};
    private static final float TOP = 522, BOTTOM = 48;
    private final PDDocument document;
    private final PDType0Font font;
    private final String period;
    private final List<List<String>> headers;
    private PDPageContentStream stream;
    private float y, headerBottom;

    private RecapPdfTable(PDDocument document, PDType0Font font, String period, List<String> headers) throws IOException {
        this.document = document;
        this.font = font;
        this.period = period;
        this.headers = wrapCells(headers);
    }

    static byte[] render(String period, List<String> headers, List<List<String>> rows) {
        try (var document = new PDDocument();
             var fontInput = RecapPdfTable.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf");
             var output = new ByteArrayOutputStream()) {
            if (fontInput == null) throw new IOException("Bundled PDF font is missing");
            document.setVersion(1.4f);
            var table = new RecapPdfTable(document, PDType0Font.load(document, fontInput), period, headers);
            try {
                table.newPage();
                if (rows.isEmpty()) table.text("Tidak ada data untuk filter yang dipilih.", MARGIN, table.y - 20, 10);
                for (var row : rows) table.row(table.wrapCells(row));
            } finally {
                if (table.stream != null) table.stream.close();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Gagal membuat ekspor PDF", exception);
        }
    }

    private void newPage() throws IOException {
        if (stream != null) stream.close();
        var page = new PDPage(new PDRectangle(842, 595));
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        text("REKAP ADMINISTRASI PRISM", MARGIN, 559, 13);
        text(period, MARGIN, 539, 9);
        text("Halaman " + document.getNumberOfPages(), MARGIN, 28, 8);
        y = TOP;
        draw(headers, 0, maxLines(headers));
        headerBottom = y;
    }

    private void row(List<List<String>> cells) throws IOException {
        int total = maxLines(cells);
        float height = total * LEADING + 2 * PADDING;
        // Ordinary rows stay together. Rows taller than a page are split without dropping text.
        if (height > y - BOTTOM && y < headerBottom) newPage();
        for (int offset = 0; offset < total;) {
            int fit = (int) ((y - BOTTOM - 2 * PADDING) / LEADING);
            if (fit < 1) { newPage(); continue; }
            int count = Math.min(fit, total - offset);
            draw(cells, offset, count);
            offset += count;
            if (offset < total) newPage();
        }
    }

    private void draw(List<List<String>> cells, int offset, int count) throws IOException {
        float height = count * LEADING + 2 * PADDING;
        float x = MARGIN;
        stream.setLineWidth(0.4f);
        for (int column = 0; column < WIDTHS.length; column++) {
            stream.addRect(x, y - height, WIDTHS[column], height);
            stream.stroke();
            List<String> lines = cells.get(column);
            for (int line = 0; line < count && offset + line < lines.size(); line++) {
                text(lines.get(offset + line), x + PADDING, y - PADDING - SIZE - line * LEADING, SIZE);
            }
            x += WIDTHS[column];
        }
        y -= height;
    }

    private void text(String value, float x, float y, float size) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private List<List<String>> wrapCells(List<String> cells) throws IOException {
        List<List<String>> wrapped = new ArrayList<>();
        for (int i = 0; i < WIDTHS.length; i++) wrapped.add(wrap(supportedText(cells.get(i)), WIDTHS[i] - 2 * PADDING));
        return wrapped;
    }

    private String supportedText(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r') { result.append('\n'); return; }
            if (cp == '\t') { result.append(' '); return; }
            String character = new String(Character.toChars(cp));
            try {
                font.encode(character);
                result.append(character);
            } catch (IllegalArgumentException | IOException exception) {
                // Preserve the identity of glyphs beyond this font's coverage instead of silently losing them.
                result.append(String.format(Locale.ROOT, "[U+%04X]", cp));
            }
        });
        return result.toString();
    }

    private List<String> wrap(String value, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                int end = 0, lastSpace = -1;
                float measured = 0;
                while (end < remaining.length()) {
                    int next = end + Character.charCount(remaining.codePointAt(end));
                    float advance = font.getStringWidth(remaining.substring(end, next)) * SIZE / 1000;
                    if (measured + advance > width) break;
                    measured += advance;
                    if (Character.isWhitespace(remaining.codePointAt(end))) lastSpace = end;
                    end = next;
                }
                if (end == 0) throw new IllegalStateException("A glyph exceeds the cell width");
                if (end < remaining.length() && lastSpace > 0) end = lastSpace + 1;
                lines.add(remaining.substring(0, end));
                remaining = remaining.substring(end);
            }
            if (paragraph.isEmpty()) lines.add("");
        }
        return lines;
    }

    private int maxLines(List<List<String>> cells) {
        return cells.stream().mapToInt(List::size).max().orElse(1);
    }
}
