package com.github.kafeyangasli.prism.feature.reservation.controller;

import com.github.kafeyangasli.prism.feature.reservation.service.ProposalDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;

@Controller
@PreAuthorize("hasAnyRole('PENGGUNA','PETUGAS','ADMIN')")
public class ProposalDocumentController {
    private final ProposalDocumentService documents;

    public ProposalDocumentController(ProposalDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/documents/reservations/{id}/validated-proposal")
    public ResponseEntity<Resource> validatedProposal(@PathVariable long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("proposal-tervalidasi-" + id + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(documents.validatedProposal(id));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> missing(ResourceNotFoundException exception) {
        return ResponseEntity.status(404).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exception.getMessage());
    }
}
