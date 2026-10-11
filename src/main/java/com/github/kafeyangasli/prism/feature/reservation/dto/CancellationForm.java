package com.github.kafeyangasli.prism.feature.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancellationForm(
        @NotBlank(message = "Alasan pembatalan wajib diisi")
        @Size(max = 2000, message = "Alasan maksimal 2000 karakter")
        String reason) {
}
