package com.github.kafeyangasli.prism.config.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class FacilitySeedSourceTest {
    @TempDir Path directory;

    @Test void readsEveryActualRecordIncludingStockAndQuotedCommas() throws Exception {
        var entries = FacilitySeedSource.read(Path.of("initial_data"));
        assertThat(entries).hasSize(19);
        assertThat(entries).allSatisfy(e -> assertThat(e.image()).isRegularFile());
        var aula = entries.stream().filter(e -> e.code().equals("F001")).findFirst().orElseThrow();
        assertThat(aula.capacity()).isEqualTo(300);
        assertThat(aula.name()).isEqualTo("Gedung Prof Soedarto");
        assertThat(aula.typeCode()).isEqualTo("AULA");
        assertThat(aula.description()).isEqualTo("Aula untuk seminar, kuliah umum, dan kegiatan mahasiswa berskala besar.");
        assertThat(entries.stream().filter(e -> e.stock() != null).map(FacilitySeedSource.Entry::stock))
                .containsExactly(25, 55, 100, 40, 75);
        assertThat(entries.stream().filter(e -> java.util.Set.of("F002", "F003", "F007", "F011", "F012", "F013", "F019").contains(e.code())))
                .hasSize(7).allSatisfy(e -> assertThat(e.location()).isEqualTo("FSM - UNDIP"));
        assertThat(entries.stream().filter(e -> e.stock() != null))
                .hasSize(5).allSatisfy(e -> assertThat(e.location()).isEqualTo("ICT - UNDIP"));
        assertThat(entries.stream().filter(e -> e.code().equals("F005")).findFirst().orElseThrow().location())
                .isEqualTo("Perpustakaan UNDIP");
        assertThat(entries.stream().filter(e -> java.util.Set.of("F004", "F008", "F009").contains(e.code())))
                .hasSize(3).allSatisfy(e -> {
                    assertThat(e.typeCode()).isEqualTo("LAPANGAN");
                    assertThat(e.typeName()).isEqualTo("Lapangan");
                });
        assertThat(entries.stream().filter(e -> e.code().equals("F004")).findFirst().orElseThrow().capacity()).isEqualTo(100);
        var acintya = entries.stream().filter(e -> e.code().equals("F019")).findFirst().orElseThrow();
        assertThat(acintya.name()).isEqualTo("Acintya Prasada");
        assertThat(acintya.typeCode()).isEqualTo("AULA");
        assertThat(acintya.capacity()).isEqualTo(150);
        assertThat(acintya.description()).isNull();
    }

    @Test void optionalMetadataSuppliesLocationAndTypeWithoutChangingDescriptionParsing() throws Exception {
        Files.writeString(directory.resolve("hall.txt"), "F001,Gedung Baru,\"Baris satu, dua\n  baris tiga\",20");
        Files.writeString(directory.resolve("facility-metadata.properties"),
                "F001.location=  FSM - UNDIP  \nF001.typeCode=AULA\nF001.typeName=Aula\n");
        var entry = FacilitySeedSource.read(directory).getFirst();
        assertThat(entry.location()).isEqualTo("FSM - UNDIP");
        assertThat(entry.typeCode()).isEqualTo("AULA");
        assertThat(entry.description()).isEqualTo("Baris satu, dua\n  baris tiga");
        Files.writeString(directory.resolve("facility-metadata.properties"), "F001.location= \n");
        assertThatThrownBy(() -> FacilitySeedSource.read(directory)).hasMessageContaining("hall.txt").hasMessageContaining("location");
        Files.writeString(directory.resolve("facility-metadata.properties"), "F001.typeCode=AULA\n");
        assertThatThrownBy(() -> FacilitySeedSource.read(directory)).hasMessageContaining("hall.txt").hasMessageContaining("typeName");
        assertThat(FacilitySeedSource.parse("legacy.txt", "F001,Aula,20").location()).isEqualTo("Universitas Diponegoro");
    }

    @Test void preservesMultilineCommasQuotesAndUnusualDescriptionFormatting() {
        var entry = FacilitySeedSource.parse("multiline.txt", "\uFEFF F001 , Aula , \" Baris satu, dua\r\n  baris \"\"tiga\"\". \" , 20 \r\n");
        assertThat(entry.code()).isEqualTo("F001");
        assertThat(entry.description()).isEqualTo("Baris satu, dua\n  baris \"tiga\".");
        assertThat(FacilitySeedSource.parse("unquoted.txt", "F001,Aula,Teks, dengan, koma,20").description())
                .isEqualTo("Teks, dengan, koma");
        assertThat(FacilitySeedSource.parse("odd.txt", "F001,Aula,\"Unbalanced quotation,20").description())
                .isEqualTo("\"Unbalanced quotation");
    }

    @Test void missingEmptyAndWhitespaceDescriptionsBecomeNull() {
        for (String record : new String[]{"F001,Aula,20", "F001,Aula,,20", "F001,Aula, \" \n \" ,20",
                "F014,Proyektor,Stok tersedia: 25"}) {
            assertThat(FacilitySeedSource.parse("empty.txt", record).description()).isNull();
        }
    }

    @Test void invalidRequiredDataAndOverlongDescriptionsFailWithFilename() {
        for (String record : new String[]{"F001,Aula,", "F001,Aula,desc,0", "F001,Aula,desc,-1",
                "F001,Aula,desc,2147483648", "F001,,desc,20", "F001,Unknown,desc,20",
                "F014,Proyektor,Stok tersedia: 0", "F001,Aula," + "x".repeat(2001) + ",20"}) {
            assertThatThrownBy(() -> FacilitySeedSource.parse("broken.txt", record))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("broken.txt");
        }
    }

    @Test void rejectsDuplicateCodesAndEmptySourcesAndIgnoresUnmatchedImages() throws Exception {
        assertThatThrownBy(() -> FacilitySeedSource.read(directory)).hasMessageContaining("no facility .txt");
        Files.writeString(directory.resolve("a.txt"), "F001,Aula,20");
        Files.writeString(directory.resolve("photo.jpg"), "not a text record");
        assertThat(FacilitySeedSource.read(directory)).hasSize(1);
        assertThat(FacilitySeedSource.read(directory).getFirst().image()).isNull();
        Files.writeString(directory.resolve("b.txt"), "f001,Aula,20");
        assertThatThrownBy(() -> FacilitySeedSource.read(directory)).hasMessageContaining("b.txt").hasMessageContaining("duplicate");
    }

    @Test void matchesCompanionsByBasenameCaseInsensitivelyAndRejectsAmbiguousImages() throws Exception {
        Files.writeString(directory.resolve("1. aula.txt"), "F001,Aula,20");
        Path companion = directory.resolve("1. AULA.JPEG");
        Files.write(companion, new byte[]{1});
        assertThat(FacilitySeedSource.read(directory).getFirst().image()).isEqualTo(companion);
        Files.write(directory.resolve("1. aula.png"), new byte[]{1});
        assertThatThrownBy(() -> FacilitySeedSource.read(directory)).hasMessageContaining("1. aula.txt")
                .hasMessageContaining("multiple companion images");
    }
}
