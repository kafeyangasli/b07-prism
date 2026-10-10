package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CreateBlockageRequest {

    private Long facilityId;
    private Long blockageTypeId;
    private Long reportId;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime plannedEndAt;
    private String publicReason;
    private String internalNote;
    private boolean confirmed;
    private String confirmationToken;

    public CreateBlockageRequest(Long facilityId, Long blockageTypeId, Long reportId, LocalDateTime startAt,
                                LocalDateTime plannedEndAt, String publicReason, String internalNote) {
        this.facilityId = facilityId;
        this.blockageTypeId = blockageTypeId;
        this.reportId = reportId;
        this.startAt = startAt;
        this.plannedEndAt = plannedEndAt;
        this.publicReason = publicReason;
        this.internalNote = internalNote;
    }
}
