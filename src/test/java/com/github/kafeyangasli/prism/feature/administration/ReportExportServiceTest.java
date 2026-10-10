package com.github.kafeyangasli.prism.feature.administration;

import com.github.kafeyangasli.prism.feature.administration.dto.*;
import com.github.kafeyangasli.prism.feature.administration.service.ReportExportService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class ReportExportServiceTest {
    final ReportExportService exporter = new ReportExportService();
    RecapResult result(List<FacilityRecapRow> rows) {
        return new RecapResult(new RecapFilter(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), null, null, null),
                rows, 2, 26L, 1, LocalDateTime.of(2026, 9, 22, 10, 0));
    }
    FacilityRecapRow row(String name, String location) {
        return new FacilityRecapRow(1, "LAB-01", name, "Laboratorium", location, 2, 26L, new BigDecimal("7.69"), 1);
    }

    @Test void unicodeAndLongCellsStayWithinColumnsAcrossPages() throws Exception {
        String name = "Ruang café – Étika “Merdeka” Ω Ж ";
        var rows = new ArrayList<FacilityRecapRow>();
        for (int i = 0; i < 65; i++) rows.add(row(name.repeat(4), "Gedung Informatika Lantai Dua, Jalan Prof. Soedarto ".repeat(3)));
        byte[] bytes = exporter.toPdf(result(rows));
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            var text = new BoundsStripper();
            String extracted = text.getText(pdf);
            assertThat(extracted).contains("café", "Étika", "Ω", "Ж", "7.69");
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                var stripper = new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page);
                assertThat(stripper.getText(pdf)).contains("Kode", "Fasilitas", "Frekuensi", "Laporan", "Halaman " + page);
            }
            Path samples = Path.of("target/pdf-qa"); Files.createDirectories(samples);
            Files.write(samples.resolve("wrapped-unicode.pdf"), bytes);
            var renderer = new PDFRenderer(pdf);
            ImageIO.write(renderer.renderImageWithDPI(0, 120), "png", samples.resolve("first-page.png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(pdf.getNumberOfPages() - 1, 120), "png", samples.resolve("last-page.png").toFile());
        }
    }

    @Test void aSingleOversizedCellContinuesAndKeepsTheLastColumn() throws Exception {
        String name = "LongUnbrokenFacilityName".repeat(500);
        byte[] bytes = exporter.toPdf(result(List.of(row(name + " END_MARKER", "Final location"))));
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            var stripper = new BoundsStripper();
            String extracted = stripper.getText(pdf);
            assertThat(extracted).contains("END_MARKER", "Final location", "7.69");
            assertThat(stripper.nameText.toString().replaceAll("\\s", "")).contains(name);
            for (var page : pdf.getPages()) {
                var font = page.getResources().getFont(page.getResources().getFontNames().iterator().next());
                assertThat(font.isEmbedded()).isTrue();
            }
        }
    }

    @Test void unsupportedGlyphIdentityIsExplicitAndEmptyPdfIsValid() throws Exception {
        try (var pdf = Loader.loadPDF(exporter.toPdf(result(List.of(row("Room 🦄", "Floor")))))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("[U+1F984]").doesNotContain("?");
        }
        try (var pdf = Loader.loadPDF(exporter.toPdf(result(List.of())))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("Tidak ada data");
        }
    }

    static class BoundsStripper extends PDFTextStripper {
        final StringBuilder nameText = new StringBuilder();
        BoundsStripper() throws java.io.IOException { super(); }
        @Override protected void processTextPosition(TextPosition text) {
            assertThat(text.getXDirAdj()).isGreaterThanOrEqualTo(36);
            assertThat(text.getXDirAdj() + text.getWidthDirAdj()).isLessThanOrEqualTo(807);
            assertThat(text.getYDirAdj()).isBetween(20f, 570f);
            if (text.getXDirAdj() >= 113 && text.getXDirAdj() < 261 && text.getYDirAdj() > 110 && text.getYDirAdj() < 547)
                nameText.append(text.getUnicode());
            if (text.getYDirAdj() > 80 && text.getYDirAdj() < 547) {
                float[] edges = {36,108,266,351,496,566,646,718,806};
                for (int i = 0; i < edges.length - 1; i++) {
                    if (text.getXDirAdj() >= edges[i] && text.getXDirAdj() < edges[i+1]) {
                        assertThat(text.getXDirAdj() + text.getWidthDirAdj()).isLessThanOrEqualTo(edges[i+1] - 4);
                    }
                }
            }
            super.processTextPosition(text);
        }
    }
}
