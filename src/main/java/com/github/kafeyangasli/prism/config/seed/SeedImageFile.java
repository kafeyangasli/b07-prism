package com.github.kafeyangasli.prism.config.seed;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Streams a local seed image through the same validation/storage API as facility uploads. */
record SeedImageFile(Path path, long size) implements MultipartFile {
    @Override public String getName() { return "image"; }
    @Override public String getOriginalFilename() { return path.getFileName().toString(); }
    @Override public String getContentType() { return null; }
    @Override public boolean isEmpty() { return size == 0; }
    @Override public long getSize() { return size; }
    @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
    @Override public void transferTo(File destination) throws IOException {
        Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
