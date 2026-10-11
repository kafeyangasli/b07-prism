package com.github.kafeyangasli.prism.feature.report.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class CreateReportRequest {

    @Positive
    private Long facilityId;
    @Positive
    private Long reservationId;
    @NotBlank @Size(max = 80)
    private String category;
    @NotBlank @Size(max = 2000)
    private String description;

    @AssertTrue(message = "Pilih fasilitas atau reservasi.")
    public boolean isFacilitySelected() {
        return facilityId != null || reservationId != null;
    }
}
