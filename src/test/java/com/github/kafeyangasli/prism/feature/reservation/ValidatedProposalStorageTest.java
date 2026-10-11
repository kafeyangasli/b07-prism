package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static com.github.kafeyangasli.prism.support.ProposalPdf.*;
import static org.assertj.core.api.Assertions.*;

class ValidatedProposalStorageTest {
    @TempDir Path directory;
    ProposalStorageService storage;

    @BeforeEach void prepare() {
        storage = new ProposalStorageService(directory.toString());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void clear() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void committedPdfRetainsExactBytesAndSurvivesStorageRestart() throws Exception {
        var pdf = upload();
        String name = storage.storeValidatedForTransaction(pdf);
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(name).matches("validated-[0-9a-f-]{36}\\.pdf");
        assertThat(Files.readAllBytes(directory.resolve(name))).isEqualTo(pdf.getBytes());
        assertThat(new ProposalStorageService(directory.toString()).load(name).exists()).isTrue();
    }

    @Test void rollbackRemovesOnlyTheNewValidatedPdfAndPreservesOriginal() throws Exception {
        String original = storage.store(new MockMultipartFile("proposal", "original.pdf", "application/pdf", bytes()));
        String validated = storage.storeValidatedForTransaction(upload());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(directory.resolve(validated)).doesNotExist();
        assertThat(directory.resolve(original)).exists();
    }

    @Test void unknownCommitOutcomeDoesNotDeletePotentiallyReferencedPdf() throws Exception {
        String name = storage.storeValidatedForTransaction(upload());
        complete(TransactionSynchronization.STATUS_UNKNOWN);
        assertThat(directory.resolve(name)).exists();
    }

    @Test void originalReadFailureLeavesNoPartialFile() {
        var broken = new MockMultipartFile("proposal", "original.pdf", "application/pdf", bytes()) {
            @Override public java.io.InputStream getInputStream() {
                return new java.io.InputStream() {
                    int read;
                    @Override public int read() throws IOException {
                        if (read++ > 0) throw new IOException("Failed upload read");
                        return '%';
                    }
                };
            }
        };
        assertThatThrownBy(() -> storage.store(broken)).isInstanceOf(IOException.class);
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test void originalSizeLimitIsEnforcedByActualBytes() {
        var misleading = new MockMultipartFile("proposal", "original.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> storage.store(misleading)).isInstanceOf(IOException.class);
        assertThat(directory.toFile().list()).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2})
    void originalProposalCleanupRequiresConfirmedRollback(int status) throws Exception {
        String name = storage.storeForTransaction(upload());
        complete(status);
        assertThat(Files.exists(directory.resolve(name))).isEqualTo(status != TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test void boundedReadingEnforcesConfiguredLimitEvenIfReportedSizeIsFalse() {
        ReflectionTestUtils.setField(storage, "validatedMaxSize", DataSize.ofBytes(128));
        var misleading = new MockMultipartFile("validatedProposal", "approved.pdf", "application/pdf", new byte[129]) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> storage.storeValidatedForTransaction(misleading)).isInstanceOf(BusinessRuleException.class);
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test void spoofedMimeDoesNotReplaceContentValidation() throws Exception {
        assertThatThrownBy(() -> storage.storeValidatedForTransaction(
                new MockMultipartFile("validatedProposal", "approved.pdf", "application/pdf", "%PDF-fake".getBytes())))
                .isInstanceOf(BusinessRuleException.class);
        String name = storage.storeValidatedForTransaction(
                new MockMultipartFile("validatedProposal", "approved.pdf", "text/plain", bytes()));
        assertThat(directory.resolve(name)).exists();
    }

    @Test void diskFailureLeavesNoPartialPdf() throws Exception {
        Path obstacle = directory.resolve("not-a-directory");
        Files.writeString(obstacle, "occupied");
        var broken = new ProposalStorageService(obstacle.toString());
        assertThatThrownBy(() -> broken.storeValidatedForTransaction(upload())).isInstanceOf(IOException.class);
        assertThat(directory.toFile().list()).containsExactly("not-a-directory");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test void traversalAndAbsoluteNamesCannotBeStoredOrRetrieved() throws Exception {
        for (String unsafe : new String[]{"../other.pdf", "sub/file.pdf", "sub\\file.pdf", "C:\\other.pdf", "file.pdf:secret"}) {
            assertThatThrownBy(() -> storage.storeValidatedForTransaction(
                    new MockMultipartFile("validatedProposal", unsafe, "application/pdf", bytes())))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> storage.load(unsafe)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
