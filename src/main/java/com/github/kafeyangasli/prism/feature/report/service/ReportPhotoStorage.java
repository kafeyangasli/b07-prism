package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Private files are served only after report authorization, never as static resources. */
@Service
public class ReportPhotoStorage {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final Path root;

    public ReportPhotoStorage(@Value("${prism.storage.reports:./uploads}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new BusinessRuleException("Foto laporan wajib diunggah.");
        }
        if (photo.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("Ukuran foto maksimal 10 MB.");
        }
        try (var input = photo.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new BusinessRuleException("Ukuran foto maksimal 10 MB.");
            }
            String extension;
            try (var imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw invalidPhoto();
                var reader = readers.next();
                try {
                    reader.setInput(imageInput);
                    extension = switch (reader.getFormatName().toLowerCase(java.util.Locale.ROOT)) {
                        case "jpeg", "jpg" -> "jpg";
                        case "png" -> "png";
                        default -> throw invalidPhoto();
                    };
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw invalidPhoto();
                    if (reader.read(0) == null) throw invalidPhoto();
                } finally {
                    reader.dispose();
                }
            } catch (IOException e) {
                throw invalidPhoto();
            }
            Files.createDirectories(root);
            String name = UUID.randomUUID() + "." + extension;
            Files.write(root.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            return name;
        } catch (IOException e) {
            throw new IllegalStateException("Foto laporan gagal disimpan.", e);
        }
    }

    public byte[] load(String name) {
        // Also permits safe legacy upload names, but never directories or symlinks.
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("..")) {
            throw missingPhoto();
        }
        Path file = root.resolve(name).normalize();
        try {
            if (!file.getParent().equals(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().getParent().equals(root.toRealPath())) throw missingPhoto();
            try (var input = Files.newInputStream(file)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw missingPhoto();
                return bytes;
            }
        } catch (IOException e) {
            throw missingPhoto();
        }
    }

    public void delete(String name) {
        if (name != null && name.matches("[0-9a-f-]{36}\\.(jpg|png)")) {
            try {
                Files.deleteIfExists(root.resolve(name));
            } catch (IOException e) {
                throw new IllegalStateException("Foto laporan gagal dihapus.", e);
            }
        }
    }

    private BusinessRuleException invalidPhoto() {
        return new BusinessRuleException("Foto harus berupa gambar JPEG atau PNG yang valid (maksimal 20 megapiksel).");
    }

    private ResourceNotFoundException missingPhoto() {
        return new ResourceNotFoundException("Foto laporan tidak ditemukan.");
    }
}
