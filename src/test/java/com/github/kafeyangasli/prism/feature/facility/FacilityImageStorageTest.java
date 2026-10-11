package com.github.kafeyangasli.prism.feature.facility;

import com.github.kafeyangasli.prism.feature.facility.service.FacilityImageStorage;
import com.github.kafeyangasli.prism.shared.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class FacilityImageStorageTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2})
    void imageCleanupRequiresConfirmedRollback(int status) throws Exception {
        var storage = new FacilityImageStorage(root.toString(), 1024 * 1024);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            String name = storage.storeForTransaction(new org.springframework.mock.web.MockMultipartFile("image", "photo.png", "image/png", output.toByteArray()));
            for (var synchronization : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(status);
            }
            assertThat(java.nio.file.Files.exists(root.resolve(name))).isEqualTo(
                    status != org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
    @TempDir Path root;
    @Test void survivesRestartAndRejectsMalformedUnsupportedAndUnsafePaths() throws Exception {
        var storage = new FacilityImageStorage(root.toString(), 1024);
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        String name = storage.store(new MockMultipartFile("images", "../../name.html", "text/html", png));
        assertThat(name).matches("[0-9a-f-]{36}\\.png");
        assertThat(new FacilityImageStorage(root.toString(), 1024).load(name)).isEqualTo(png);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("images", Arrays.copyOf(png, 24))))
                .isInstanceOf(BusinessRuleException.class);
        output.reset(); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", output);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("images", "forged.png", "image/png", output.toByteArray())))
                .isInstanceOf(BusinessRuleException.class);
        for (String unsafe : new String[]{"../secret.png", "sub/file.png", "C:\\secret.png", "sub\\file.png", "name.png:secret"})
            assertThatThrownBy(() -> storage.load(unsafe)).isInstanceOf(ResourceNotFoundException.class);
        storage.delete(name); assertThat(root.resolve(name)).doesNotExist();
    }
}
