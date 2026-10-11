package com.github.kafeyangasli.prism.shared.exception.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationReasonCode;

@Service
public class ProposalStorageService {
    private static final Logger log = LoggerFactory.getLogger(ProposalStorageService.class);
    private static final long MAX_BYTES =
            10L * 1024L * 1024L;

    private static final String[] ALLOWED_EXTENSIONS = {
            "pdf",
            "doc",
            "docx"
    };

    private final Path root;

    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private DataSize validatedMaxSize = DataSize.ofMegabytes(10);

    public long getValidatedMaxBytes() {
        return validatedMaxSize.toBytes();
    }

    /** Store the exact approved bytes; parsing checks format, never signature authenticity. */
    public String storeValidatedForTransaction(MultipartFile file) throws IOException {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Validated proposal storage requires a transaction.");
        }
        long limit = getValidatedMaxBytes();
        if (limit <= 0 || limit >= Integer.MAX_VALUE) {
            throw new IllegalStateException("PDF upload size limit must be positive and below 2 GB.");
        }
        if (file == null || file.isEmpty()) throw invalidValidated("PDF proposal tervalidasi wajib diunggah");
        if (file.getSize() > limit) throw invalidValidated("Ukuran PDF tervalidasi melebihi batas unggahan");
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..") || filename.contains("/")
                || filename.contains("\\") || filename.contains(":") || !"pdf".equals(extensionOf(filename))) {
            throw invalidValidated("Proposal tervalidasi harus berupa berkas PDF dengan nama yang valid");
        }
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes((int) limit + 1);
        }
        if (bytes.length == 0) throw invalidValidated("PDF proposal tervalidasi kosong");
        if (bytes.length > limit) throw invalidValidated("Ukuran PDF tervalidasi melebihi batas unggahan");
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw invalidValidated("Isi proposal tervalidasi harus berupa PDF yang valid");
        }
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() < 1) throw new IOException("PDF tervalidasi harus memiliki halaman");
        } catch (IOException | RuntimeException exception) {
            throw invalidValidated("Isi proposal tervalidasi harus berupa PDF yang dapat dibaca");
        }
        Files.createDirectories(root);
        String name = "validated-" + UUID.randomUUID() + ".pdf";
        Path target = root.resolve(name);
        // CREATE_NEW forbids overwriting another document, even on a name collision.
        // Only clean up a file that this operation successfully created.
        var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW);
        try (output) {
            output.write(bytes);
        } catch (IOException exception) {
            try { Files.deleteIfExists(target); }
            catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // An unknown commit outcome may still reference the file; do not delete it.
                if (status == STATUS_ROLLED_BACK) cleanupValidated(name);
            }
        });
        return name;
    }

    private void cleanupValidated(String name) {
        if (!name.matches("validated-[0-9a-f-]{36}\\.pdf")) return;
        try { Files.deleteIfExists(root.resolve(name)); }
        catch (IOException exception) { log.warn("Unused validated proposal cleanup failed; storage maintenance is required."); }
    }

    private BusinessRuleException invalidValidated(String message) {
        return new BusinessRuleException(ReservationReasonCode.INVALID_PROPOSAL, message);
    }

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

        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !resource.exists()
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
