package com.github.kafeyangasli.prism.feature.reservation.dto;

public class ReservationForm {
    private Long facilityId;
    private String date;
    private String startAt;
    private String endAt;
    private String purpose;
    private boolean facilityFixed;

    public Long getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(Long facilityId) {
        this.facilityId = facilityId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartAt() {
        return startAt;
    }

    public void setStartAt(String startAt) {
        this.startAt = startAt;
    }

    public String getEndAt() {
        return endAt;
    }

    public void setEndAt(String endAt) {
        this.endAt = endAt;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public boolean isFacilityFixed() {
        return facilityFixed;
    }

    public void setFacilityFixed(boolean facilityFixed) {
        this.facilityFixed = facilityFixed;
    }
}
