package com.github.kafeyangasli.prism.feature.administration.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityFilters;

public record RecapFilter(
        @NotNull(message = "Tanggal awal wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull(message = "Tanggal akhir wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        Long facilityId,
        String facilityType,
        String location,
        List<String> facilityTypes,
        List<String> locations) {

    public RecapFilter(LocalDate startDate, LocalDate endDate, Long facilityId, String facilityType, String location) {
        this(startDate, endDate, facilityId, facilityType, location, List.of(), List.of());
    }

    public RecapFilter normalized() {
        return new RecapFilter(startDate, endDate, facilityId,
                normalize(facilityType), normalize(location),
                FacilityFilters.combine(facilityTypes, facilityType), FacilityFilters.combine(locations, location));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
