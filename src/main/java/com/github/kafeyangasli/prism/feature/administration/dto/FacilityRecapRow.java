package com.github.kafeyangasli.prism.feature.administration.dto;

import java.math.BigDecimal;

public record FacilityRecapRow(long facilityId, String facilityCode, String facilityName,
                               String facilityType, String location,
                               long approvedSlots, Long bookableSlots,
                               BigDecimal occupancyPercent, long issueCount) {
    public String capacityDisplay() { return bookableSlots == null ? "Tidak diketahui" : bookableSlots.toString(); }
    public String occupancyDisplay() {
        return occupancyPercent == null ? (bookableSlots == null ? "Tidak diketahui" : "N/A (kapasitas nol)")
                : occupancyPercent.toPlainString();
    }
}
