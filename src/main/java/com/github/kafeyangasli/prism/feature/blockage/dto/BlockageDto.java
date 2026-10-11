package com.github.kafeyangasli.prism.feature.blockage.dto;

import com.github.kafeyangasli.prism.feature.blockage.model.*;
import com.github.kafeyangasli.prism.feature.user.model.User;
import java.time.LocalDateTime;

public record BlockageDto(Long id, Reference facility, Reference blockageType, Long reportId,
                         LocalDateTime startAt, LocalDateTime plannedEndAt, LocalDateTime actualEndAt,
                         BlockageStatus status, String publicReason, String internalNote,
                         Reference createdBy, Reference endedBy, String earlyCompletionReason) {
    public record Reference(Long id, String name) {}
    private static Reference actor(User user) { return user == null ? null : new Reference(user.getId(), user.getName()); }
    public static BlockageDto from(FacilityBlockage b) {
        return new BlockageDto(b.getId(), new Reference(b.getFacility().getId(), b.getFacility().getName()),
                new Reference(b.getBlockageType().getId(), b.getBlockageType().getName()), b.getReport() == null ? null : b.getReport().getId(),
                b.getStartAt(), b.getPlannedEndAt(), b.getActualEndAt(), b.getStatus(), b.getPublicReason(), b.getInternalNote(),
                actor(b.getCreatedBy()), actor(b.getEndedBy()), b.getEarlyCompletionReason());
    }
}
