package com.github.kafeyangasli.prism.feature.report.model;

import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_facility_status_created", columnList = "facility_id, status, created_at"),
        @Index(name = "idx_reports_user_created", columnList = "user_id, created_at")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "photo_path", length = 1000)
    private String photoPath;

    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "report", fetch = FetchType.LAZY)
    private List<FacilityBlockage> blockages = new ArrayList<>();

    protected Report() {
    }

    public Report(User user, Facility facility, String category, String description,
                  String photoPath, ReportStatus status) {
        this.user = user;
        this.facility = facility;
        this.category = category;
        this.description = description;
        this.photoPath = photoPath;
        this.status = status;
    }

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
