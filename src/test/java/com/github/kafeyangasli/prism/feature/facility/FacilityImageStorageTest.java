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
