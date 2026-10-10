package com.github.kafeyangasli.prism.shared.storage;

import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;


import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.Set;

/** Disk image validation and safe identifiers, shared by public facilities and private reports. */
public class LocalImageStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);
    private final int maxBytes;
    private final Path root;
    private final Set<String> allowedFormats;
    private final int normalizedMaxDimension;

    public LocalImageStorage(String directory, int maxBytes) {
        this(directory, maxBytes, Set.of("jpg", "png"), 0);
    }

    // Opt-in normalization: facility/report formats and original-byte storage remain unchanged.
    public LocalImageStorage(String directory, int maxBytes, Set<String> allowedFormats, int normalizedMaxDimension) {
        if (maxBytes <= 0 || maxBytes > 100 * 1024 * 1024)
            throw new IllegalArgumentException("Image size limit must be between 1 byte and 100 MB.");
        this.maxBytes = maxBytes;
        this.allowedFormats = Set.copyOf(allowedFormats);
        this.normalizedMaxDimension = normalizedMaxDimension;
        if (normalizedMaxDimension < 0) throw new IllegalArgumentException("Image dimension must not be negative.");
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
                        case "webp" -> "webp";
                        default -> throw invalidPhoto();
                    };
                    if (!allowedFormats.contains(extension)) throw invalidPhoto();
                    if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0
                            || (long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw invalidPhoto();
                    boolean[] warning = {false};
                    reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null || (normalizedMaxDimension > 0 && warning[0])) throw invalidPhoto();
                    if (normalizedMaxDimension > 0) {
                        bytes = normalize(decoded);
                        extension = "png";
                    }
                } finally {
                    reader.dispose();
                }
            } catch (IOException | RuntimeException e) {
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

    /** Keep database references valid: only remove the old file after commit. */
    public void deleteAfterCommit(String name) {
        if (name == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup(name); }
        });
    }

    /** A rolled-back reference must not leave its newly written file behind. */
    public String storeForTransaction(MultipartFile upload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Image storage requires a transaction.");
        String name = store(upload);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cleanup(name);
            }
        });
        return name;
    }

    private void cleanup(String name) {
        try { delete(name); }
        catch (IllegalStateException exception) {
            // Do not report a committed database change as failed. An unused
            // file is safer than deleting an image still referenced by the DB.
            log.warn("Unused image cleanup failed; storage maintenance is required.");
        }
    }

    private byte[] normalize(BufferedImage decoded) throws IOException {
        double scale = Math.min(1.0, (double) normalizedMaxDimension / Math.max(decoded.getWidth(), decoded.getHeight()));
        BufferedImage clean = new BufferedImage(Math.max(1, (int) (decoded.getWidth() * scale)),
                Math.max(1, (int) (decoded.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
        var graphics = clean.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(decoded, 0, 0, clean.getWidth(), clean.getHeight(), null);
        } finally { graphics.dispose(); }
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(clean, "png", output)) throw invalidPhoto();
        if (output.size() > maxBytes) throw invalidPhoto();
        return output.toByteArray();
    }

    private BusinessRuleException invalidPhoto() {
        return new BusinessRuleException(allowedFormats.contains("webp")
                ? "Gambar harus berupa JPEG, PNG, atau WebP yang valid (maksimal 20 megapiksel dan sesuai batas ukuran)."
                : "Gambar harus berupa gambar JPEG atau PNG yang valid (maksimal 20 megapiksel).");
    }

    private ResourceNotFoundException missingPhoto() {
        return new ResourceNotFoundException("Gambar tidak ditemukan.");
    }
}

