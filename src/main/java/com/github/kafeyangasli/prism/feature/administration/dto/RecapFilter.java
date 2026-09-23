package com.github.kafeyangasli.prism.feature.administration.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record RecapFilter(
        @NotNull(message = "Tanggal awal wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull(message = "Tanggal akhir wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        Long facilityId,
        String facilityType,
        String location) {

    public RecapFilter normalized() {
        return new RecapFilter(startDate, endDate, facilityId,
                normalize(facilityType), normalize(location));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
