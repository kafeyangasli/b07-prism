package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UpdateBlockageRequest {

    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime plannedEndAt;
    private String publicReason;
    private String internalNote;
    private Boolean openEnded;
    private boolean confirmed;
    private String confirmationToken;

    public UpdateBlockageRequest(LocalDateTime plannedEndAt, String publicReason, String internalNote) {
        this.plannedEndAt = plannedEndAt;
        this.publicReason = publicReason;
        this.internalNote = internalNote;
    }
}
