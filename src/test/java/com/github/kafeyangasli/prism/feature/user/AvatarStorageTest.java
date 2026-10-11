package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.service.AvatarStorage;
import com.github.kafeyangasli.prism.shared.storage.LocalImageStorage;
import com.github.kafeyangasli.prism.shared.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AvatarStorageTest {
    @TempDir Path root;
    static final String LOSSLESS_WEBP = "UklGRh4AAABXRUJQVlA4TBEAAAAvAkAAAAdQlCKXrP+BiOh/AAA=";
    static final String LOSSY_WEBP = "UklGRjQAAABXRUJQVlA4ICgAAABwAQCdASoDAAIAAUAmJaACdAFAAAD+73QK5f+4OP/7g4//uDj9eAAA";

    static MockMultipartFile picture(String format) throws IOException {
        byte[] bytes;
        if (format.startsWith("webp")) bytes = Base64.getDecoder().decode(format.equals("webp-lossy") ? LOSSY_WEBP : LOSSLESS_WEBP);
        else {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), format, output);
            bytes = output.toByteArray();
        }
        // Spoofed filename and MIME must never select storage format or location.
        return new MockMultipartFile("image", "../../unsafe.html", "text/html", bytes);
    }

    @ParameterizedTest @ValueSource(strings = {"png", "jpeg", "webp", "webp-lossy"})
    void decodesActualContentAndNormalizesToPngAcrossRestarts(String format) throws Exception {
        var storage = new AvatarStorage(root.toString(), 5242880);
        String name = storage.store(picture(format));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
        byte[] bytes = new AvatarStorage(root.toString(), 5242880).load(name);
        assertThat(Arrays.copyOf(bytes, 8)).containsExactly((byte) 137, (byte) 80, (byte) 78, (byte) 71, (byte) 13, (byte) 10, (byte) 26, (byte) 10);
        var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded.getWidth()).isEqualTo(3);
        assertThat(decoded.getHeight()).isEqualTo(2);
    }
    @Test void rejectsEmptyMalformedTruncatedAndUnsupportedContent() throws Exception {
        var storage = new AvatarStorage(root.toString(), 5242880);
        for (byte[] bytes : List.of(new byte[0], "<svg onload='bad()'/>".getBytes(), picture("gif").getBytes(),
                Arrays.copyOf(picture("png").getBytes(), 28), Arrays.copyOf(picture("webp").getBytes(), 22))) {
            assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "fake.png", "image/png", bytes)))
                    .isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> storage.store(null)).isInstanceOf(BusinessRuleException.class);
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
    }
    @Test void enforcesConfiguredLimitEvenWhenUploadSizeHeaderLies() {
        var storage = new AvatarStorage(root.toString(), 128);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", new byte[129])))
                .isInstanceOf(BusinessRuleException.class);
        MultipartFile misleading = new MockMultipartFile("image", new byte[129]) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> storage.store(misleading)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void stripsTrailingPayloadAndBoundsOutputDimensions() throws Exception {
        var storage = new AvatarStorage(root.toString(), 5242880);
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2048, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        output.write("<script>avatar-payload</script>".getBytes());
        String name = storage.store(new MockMultipartFile("image", output.toByteArray()));
        byte[] stored = storage.load(name);
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("avatar-payload");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(decoded.getWidth()).isEqualTo(1024);
        assertThat(decoded.getHeight()).isEqualTo(1);
    }
    @Test void refusesTraversalAndUnsafeDeletesAndPreservesOtherFiles() throws Exception {
        var storage = new AvatarStorage(root.toString(), 5242880);
        String name = storage.store(picture("png"));
        for (String unsafe : List.of("../secret.png", "sub/file.png", "C:\\secret.png", "sub\\file.png", "x.png:secret")) {
            assertThatThrownBy(() -> storage.load(unsafe)).isInstanceOf(ResourceNotFoundException.class);
            storage.delete(unsafe);
        }
        assertThat(root.resolve(name)).exists();
        storage.delete(name); assertThat(root.resolve(name)).doesNotExist();
    }
    @Test void sharedFacilityAndReportStorageStillRejectsWebp() throws Exception {
        var storage = new LocalImageStorage(root.toString(), 5242880);
        assertThatThrownBy(() -> storage.store(picture("webp"))).isInstanceOf(BusinessRuleException.class);
    }
}
