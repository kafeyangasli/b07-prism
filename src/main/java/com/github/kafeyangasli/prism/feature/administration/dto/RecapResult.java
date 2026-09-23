package com.github.kafeyangasli.prism.feature.administration.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecapResult(RecapFilter filter, List<FacilityRecapRow> rows,
                          long totalApprovedSlots, long totalBookableSlots,
                          long totalIssues, LocalDateTime generatedAt) {
}
