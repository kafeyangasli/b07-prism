package com.github.kafeyangasli.prism.shared.storage;

import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;


import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Disk image validation and safe identifiers, shared by public facilities and private reports. */
public class LocalImageStorage {
    private final int maxBytes;
    private final Path root;

    public LocalImageStorage(String directory, int maxBytes) {
        if (maxBytes <= 0 || maxBytes > 100 * 1024 * 1024)
            throw new IllegalArgumentException("Image size limit must be between 1 byte and 100 MB.");
        this.maxBytes = maxBytes;
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new BusinessRuleException("Gambar wajib diunggah.");
        }
        if (photo.getSize() > maxBytes) {
            throw new BusinessRuleException("Ukuran gambar melebihi batas yang diizinkan.");
        }
        try (var input = photo.getInputStream()) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new BusinessRuleException("Ukuran gambar melebihi batas yang diizinkan.");
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
            Path target = root.resolve(name);
            // A write can fail after creating a partial file; remove that file as well.
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                try { output.write(bytes); }
                catch (IOException e) {
                    try { output.close(); Files.deleteIfExists(target); }
                    catch (IOException cleanup) { e.addSuppressed(cleanup); }
                    throw e;
                }
            }
            return name;
        } catch (IOException e) {
            throw new IllegalStateException("Gambar gagal disimpan.", e);
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
                byte[] bytes = input.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) throw missingPhoto();
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
                throw new IllegalStateException("Gambar gagal dihapus.", e);
            }
        }
    }

    private BusinessRuleException invalidPhoto() {
        return new BusinessRuleException("Gambar harus berupa gambar JPEG atau PNG yang valid (maksimal 20 megapiksel).");
    }

    private ResourceNotFoundException missingPhoto() {
        return new ResourceNotFoundException("Gambar tidak ditemukan.");
    }
}

