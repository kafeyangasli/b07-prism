package com.github.kafeyangasli.prism.feature.blockage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBlockageRequest {

    private LocalDateTime plannedEndAt;
    private String publicReason;
    private String internalNote;
}
