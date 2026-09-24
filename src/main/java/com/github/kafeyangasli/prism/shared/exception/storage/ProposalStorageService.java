package com.github.kafeyangasli.prism.shared.exception.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProposalStorageService {
    private static final long MAX_BYTES =
            10L * 1024L * 1024L;

    private static final String[] ALLOWED_EXTENSIONS = {
            "pdf",
            "doc",
            "docx"
    };

    private final Path root;

    public ProposalStorageService(
            @Value("${prism.storage.proposals:./storage/proposals}")
            String root
    ) {

        this.root =
                Paths.get(root)
                        .toAbsolutePath()
                        .normalize();
    }

    public String store(
            MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IOException(
                    "Berkas proposal kosong"
            );
        }

        if (file.getSize() > MAX_BYTES) {
            throw new IOException(
                    "Ukuran proposal melebihi 10 MB"
            );
        }

        String originalFilename =
                StringUtils.cleanPath(
                        file.getOriginalFilename() == null
                                ? ""
                                : file.getOriginalFilename()
                );

        /*
         * Cegah path traversal.
         */
        if (originalFilename.contains("..")) {
            throw new IOException(
                    "Nama file tidak valid"
            );
        }

        String extension =
                extensionOf(originalFilename);

        if (!isAllowed(extension)) {
            throw new IOException(
                    "Format proposal harus PDF, DOC, atau DOCX"
            );
        }

        Files.createDirectories(root);

        /*
         * Jangan gunakan nama file dari user.
         *
         * Gunakan UUID agar nama file tidak dapat
         * digunakan untuk path traversal / collision.
         */
        String storedName =
                UUID.randomUUID()
                        + "."
                        + extension;

        Path target =
                root.resolve(storedName)
                        .normalize();

        /*
         * Pastikan target tetap berada di root storage.
         */
        if (!target.getParent().equals(root)) {
            throw new IOException(
                    "Lokasi file tidak valid"
            );
        }

        try (InputStream input =
                     file.getInputStream()) {

            Files.copy(
                    input,
                    target
            );
        }

        return storedName;
    }

    public Resource load(
            String storedName
    ) throws MalformedURLException {

        if (storedName == null
                || storedName.isBlank()) {

            throw new IllegalArgumentException(
                    "Nama proposal tidak valid"
            );
        }

        Path target =
                root.resolve(storedName)
                        .normalize();

        /*
         * Cegah akses ke luar folder proposal.
         */
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException(
                    "Lokasi file tidak valid"
            );
        }

        Resource resource =
                new UrlResource(
                        target.toUri()
                );

        if (!resource.exists()
                || !resource.isReadable()) {

            throw new IllegalArgumentException(
                    "Proposal tidak ditemukan"
            );
        }

        return resource;
    }

    private String extensionOf(
            String filename
    ) {

        int dot =
                filename.lastIndexOf('.');

        if (dot < 0
                || dot == filename.length() - 1) {

            return "";
        }

        return filename
                .substring(dot + 1)
                .toLowerCase(Locale.ROOT);
    }

    private boolean isAllowed(
            String extension
    ) {

        for (String allowed :
                ALLOWED_EXTENSIONS) {

            if (allowed.equals(extension)) {
                return true;
            }
        }

        return false;
    }
}
