package com.github.kafeyangasli.prism.feature.administration.dto;

import java.math.BigDecimal;

public record FacilityRecapRow(long facilityId, String facilityCode, String facilityName,
                               String facilityType, String location,
                               long approvedSlots, long bookableSlots,
                               BigDecimal occupancyPercent, long issueCount) {
}
