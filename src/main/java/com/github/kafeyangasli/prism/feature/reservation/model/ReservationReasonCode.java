package com.github.kafeyangasli.prism.feature.reservation.model;

public final class ReservationReasonCode {

    public static final String MANUAL_REJECTION = "MANUAL_REJECTION";
    public static final String SCHEDULE_CONFLICT = "SCHEDULE_CONFLICT";
    public static final String INVALID_PROPOSAL = "INVALID_PROPOSAL";
    public static final String APPROVAL_DEADLINE_EXCEEDED = "APPROVAL_DEADLINE_EXCEEDED";
    public static final String RESERVATION_START_REACHED = "RESERVATION_START_REACHED";
    public static final String CANCELLED_BY_USER = "CANCELLED_BY_USER";
    public static final String CANCELLED_BY_STAFF = "CANCELLED_BY_STAFF";

    private ReservationReasonCode() {
    }
}
