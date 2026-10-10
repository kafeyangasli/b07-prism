package com.github.kafeyangasli.prism.feature.facility.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice(assignableTypes = {AdminFacilityController.class, FacilityImageController.class})
public class FacilityImageExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> tooLarge() {
        return ResponseEntity.status(413).body("Unggahan terlalu besar. Periksa batas ukuran gambar dan total unggahan.");
    }
}
