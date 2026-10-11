package com.github.kafeyangasli.prism.feature.administration.dto;

import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationHistoryRow(long id, String requesterName, String facilityName,
                                    LocalDateTime startAt, LocalDateTime endAt, ReservationStatus status,
                                    Integer quantity, String purpose, String reasonDetail) {}
