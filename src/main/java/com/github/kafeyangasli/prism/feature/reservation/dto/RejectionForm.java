package com.github.kafeyangasli.prism.feature.reservation.dto;

import jakarta.validation.constraints.Size;

public record RejectionForm(
        @Size(max = 2000, message = "Penjelasan maksimal 2000 karakter") String reasonDetail) {
}
