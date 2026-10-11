package com.github.kafeyangasli.prism.feature.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectionForm(
        @NotBlank(message = "Alasan penolakan wajib diisi")
        @Size(max = 2000, message = "Penjelasan maksimal 2000 karakter") String reasonDetail) {
}
