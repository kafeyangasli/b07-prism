package com.github.kafeyangasli.prism.feature.administration.dto;

import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import java.time.LocalDateTime;

public record UnresolvedReportRow(long id, String facilityName, String category,
                                  ReportStatus status, LocalDateTime createdAt) {
}
