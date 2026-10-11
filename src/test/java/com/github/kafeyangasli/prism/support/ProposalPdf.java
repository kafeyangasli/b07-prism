package com.github.kafeyangasli.prism.support;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.mock.web.MockMultipartFile;

public final class ProposalPdf {
    private ProposalPdf() { }

    public static byte[] bytes() {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static MockMultipartFile upload() {
        return new MockMultipartFile("validatedProposal", "approved.pdf", "application/pdf", bytes());
    }
}
