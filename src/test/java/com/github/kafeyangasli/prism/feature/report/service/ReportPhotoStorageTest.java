package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ReportPhotoStorageTest {
    @TempDir Path directory;
    ReportPhotoStorage storage;
    @BeforeEach void setUp() { storage = new ReportPhotoStorage(directory.toString()); }

    @Test void validatesContentAndUsesOnlyServerGeneratedNames() throws Exception {
        for (String format : new String[]{"png", "jpg"}) {
            byte[] bytes = image(format);
            String name = storage.store(new MockMultipartFile("photo", "../../malicious.html", "text/html", bytes));
            assertTrue(name.matches("[0-9a-f-]{36}\\." + format));
            assertArrayEquals(bytes, storage.load(name));
            assertTrue(Files.exists(directory.resolve(name)));
        }
    }

    @Test void missingEmptyForgedAndOversizePhotosAreRejected() throws Exception {
        assertThrows(BusinessRuleException.class, () -> storage.store(null));
        assertThrows(BusinessRuleException.class, () -> storage.store(new MockMultipartFile("photo", new byte[0])));
        assertThrows(BusinessRuleException.class, () -> storage.store(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes())));
        assertThrows(BusinessRuleException.class, () -> storage.store(new MockMultipartFile("photo", new byte[10 * 1024 * 1024 + 1])));
        byte[] gif = image("gif");
        assertThrows(BusinessRuleException.class, () -> storage.store(new MockMultipartFile("photo", "photo.png", "image/png", gif)));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void loadRejectsTraversalAbsolutePathsDirectoriesAndMissingFiles() throws Exception {
        for (String name : new String[]{"../secret.jpg", "sub/photo.jpg", "sub\\photo.jpg", "C:\\secret.jpg", "photo.jpg:secret", "missing.png", "", ".."}) {
            assertThrows(ResourceNotFoundException.class, () -> storage.load(name));
        }
        assertThrows(ResourceNotFoundException.class, () -> storage.load(null));
        Files.createDirectory(directory.resolve("folder"));
        assertThrows(ResourceNotFoundException.class, () -> storage.load("folder"));
    }

    @Test void deleteCleansUpStoredPhoto() throws Exception {
        String name = storage.store(new MockMultipartFile("photo", image("png")));
        storage.delete(name);
        assertFalse(Files.exists(directory.resolve(name)));
    }

    private byte[] image(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
}
