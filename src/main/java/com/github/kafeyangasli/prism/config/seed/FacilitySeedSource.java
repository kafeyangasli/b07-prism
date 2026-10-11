package com.github.kafeyangasli.prism.config.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/** The source is a record per file, not a CSV table: descriptions can contain commas/newlines. */
final class FacilitySeedSource {
    private static final Pattern STOCK = Pattern.compile("(?is)^(.*)\\bStok\\s+tersedia\\s*:\\s*(\\d+)\\s*\\.?\\s*$");

    record Entry(String code, String name, String typeCode, String typeName,
                 int capacity, Integer stock, String description, String location, Path image) {}

    static List<Entry> read(Path directory) throws IOException {
        List<Entry> entries = new ArrayList<>();
        var codes = new HashSet<String>();
        var metadata = new Properties();
        Path metadataFile = directory.resolve("facility-metadata.properties");
        if (Files.exists(metadataFile)) {
            try (var reader = Files.newBufferedReader(metadataFile, StandardCharsets.UTF_8)) {
                metadata.load(reader);
            }
        }
        try (var files = Files.list(directory)) {
            var sourceFiles = files.filter(Files::isRegularFile).sorted().toList();
            for (Path file : sourceFiles.stream()
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .toList()) {
                Entry entry = parse(file.toString(), Files.readString(file, StandardCharsets.UTF_8), metadata);
                if (!codes.add(entry.code())) throw invalid(file.toString(), "duplicate facility code " + entry.code());
                var companions = sourceFiles.stream()
                        .filter(p -> p.getFileName().toString().matches("(?i).*\\.(jpg|jpeg|png)$"))
                        .filter(p -> basename(p).equalsIgnoreCase(basename(file)))
                        .toList();
                if (companions.size() > 1) throw invalid(file.toString(), "multiple companion images: " + companions);
                entries.add(new Entry(entry.code(), entry.name(), entry.typeCode(), entry.typeName(),
                        entry.capacity(), entry.stock(), entry.description(), entry.location(),
                        companions.isEmpty() ? null : companions.getFirst()));
            }
        }
        if (entries.isEmpty()) throw invalid(directory.toString(), "no facility .txt records found");
        return List.copyOf(entries);
    }

    private static String basename(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    static Entry parse(String source, String text) {
        return parse(source, text, new Properties());
    }

    private static Entry parse(String source, String text, Properties metadata) {
        text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        int first = text.indexOf(','), second = text.indexOf(',', first + 1);
        if (first < 0) throw invalid(source, "expected code,name,[description,]capacity or stock");
        String code = required(source, text.substring(0, first), "code", 50).toUpperCase(Locale.ROOT);
        String name = required(source, text.substring(first + 1, second < 0 ? text.length() : second), "name", 150);
        String location = required(source, metadata.getProperty(code + ".location", "Universitas Diponegoro"), "location", 150);
        String remainder = second < 0 ? "" : text.substring(second + 1).strip();
        var stockMatch = STOCK.matcher(unquote(remainder));
        if (stockMatch.matches()) {
            int stock = positive(source, stockMatch.group(2));
            return new Entry(code, name, "ALAT", "Alat", 1, stock, description(source, stockMatch.group(1)), location, null);
        }
        int last = remainder.lastIndexOf(',');
        String capacity = last < 0 ? remainder : remainder.substring(last + 1);
        String description = last < 0 ? null : description(source, remainder.substring(0, last));
        int seats = positive(source, unquote(capacity));
        String typeCode = metadata.getProperty(code + ".typeCode");
        String typeName = metadata.getProperty(code + ".typeName");
        String[] type = typeCode == null && typeName == null ? type(source, name) : new String[]{
                required(source, typeCode == null ? "" : typeCode, "typeCode", 80).toUpperCase(Locale.ROOT),
                required(source, typeName == null ? "" : typeName, "typeName", 150)};
        return new Entry(code, name, type[0], type[1], seats, null, description, location, null);
    }

    private static String[] type(String source, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("aula")) return new String[]{"AULA", "Aula"};
        if (lower.startsWith("ruang kelas")) return new String[]{"KELAS", "Kelas"};
        if (lower.startsWith("ruang")) return new String[]{"RUANG", "Ruang"};
        if (lower.startsWith("laboratorium")) return new String[]{"LABORATORIUM", "Laboratorium"};
        if (lower.startsWith("lapangan") || lower.startsWith("gor")) return new String[]{"LAPANGAN", "Lapangan"};
        if (lower.startsWith("masjid")) return new String[]{"MASJID", "Masjid"};
        throw invalid(source, "cannot derive facility type from name: " + name);
    }

    private static String unquote(String value) {
        String clean = value.strip();
        if (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1).replace("\"\"", "\"").strip();
        }
        return clean;
    }

    private static String description(String source, String value) {
        if (value == null) return null;
        String clean = unquote(value);
        if (clean.isBlank()) return null;
        if (clean.length() > 2000) throw invalid(source, "description exceeds 2000 characters");
        return clean;
    }

    private static String required(String source, String value, String field, int limit) {
        String clean = unquote(value);
        if (clean.isBlank() || clean.length() > limit) throw invalid(source, "missing or overlong " + field);
        return clean;
    }

    private static int positive(String source, String value) {
        try {
            int number = Integer.parseInt(value.strip());
            if (number > 0) return number;
        } catch (NumberFormatException ignored) {
            // Include the source file in the actionable error below.
        }
        throw invalid(source, "positive capacity/stock required, found: " + value);
    }

    private static IllegalArgumentException invalid(String source, String problem) {
        return new IllegalArgumentException("Invalid facility seed " + source + ": " + problem);
    }
}
