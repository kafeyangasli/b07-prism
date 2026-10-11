package com.github.kafeyangasli.prism.feature.facility.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "facility_images")
public class FacilityImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;
    @Setter @Column(name = "storage_path", nullable = false, length = 100)
    private String storagePath;
    @Setter @Column(name = "is_thumbnail", nullable = false)
    private boolean thumbnail;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FacilityImage() {}
    public FacilityImage(Facility facility, String storagePath, int displayOrder) {
        this.facility = facility;
        this.storagePath = storagePath;
        this.displayOrder = displayOrder;
    }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
    @Transient public String getUrl() { return "/facilities/" + facility.getId() + "/images/" + id; }
}
