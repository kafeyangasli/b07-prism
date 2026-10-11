package com.github.kafeyangasli.prism.feature.report.dto;

import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.user.model.User;
import java.time.LocalDateTime;

/** Deliberately excludes account credentials, storage paths, and entity relationships. */
public record ReportDto(Long id, Actor user, FacilityInfo facility, String category, String description,
                        ReportStatus status, String resolutionNote, Actor handledBy, LocalDateTime handledAt,
                        LocalDateTime resolvedAt, LocalDateTime createdAt, LocalDateTime updatedAt, String photoUrl) {
    public record Actor(Long id, String name) {}
    public record FacilityInfo(Long id, String name) {}
    private static Actor actor(User user) { return user == null ? null : new Actor(user.getId(), user.getName()); }
    public static ReportDto from(Report r) {
        return new ReportDto(r.getId(), actor(r.getUser()), new FacilityInfo(r.getFacility().getId(), r.getFacility().getName()),
                r.getCategory(), r.getDescription(), r.getStatus(), r.getResolutionNote(), actor(r.getHandledBy()),
                r.getHandledAt(), r.getResolvedAt(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getPhotoPath() == null ? null : "/reports/" + r.getId() + "/photo");
    }
}
