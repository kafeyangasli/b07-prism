package com.github.kafeyangasli.prism.feature.report.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {

    private Long facilityId;
    private String category;
    private String description;
    private String photoPath; // sementara (Phase 5 nanti diubah)
}