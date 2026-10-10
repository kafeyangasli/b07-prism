package com.github.kafeyangasli.prism.feature.user.service;

import com.github.kafeyangasli.prism.shared.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class AvatarStorage extends LocalImageStorage {
    public AvatarStorage(@Value("${prism.storage.avatars:./storage/avatars}") String directory,
                         @Value("${prism.avatars.max-bytes:5242880}") int maxBytes) {
        // Re-encode decoded pixels without metadata/trailing payloads and bound
        // the served avatar to 1024px on its longest edge, retaining aspect ratio.
        super(directory, maxBytes, Set.of("jpg", "png", "webp"), 1024);
    }
}
