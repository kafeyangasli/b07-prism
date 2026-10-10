package com.github.kafeyangasli.prism.feature.facility.repository;

import com.github.kafeyangasli.prism.feature.facility.model.FacilityImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FacilityImageRepository extends JpaRepository<FacilityImage, Long> {
    List<FacilityImage> findByFacilityIdOrderByDisplayOrderAscIdAsc(Long facilityId);
    Optional<FacilityImage> findByIdAndFacilityId(Long id, Long facilityId);
}
