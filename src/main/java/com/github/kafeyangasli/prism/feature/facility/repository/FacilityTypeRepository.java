package com.github.kafeyangasli.prism.feature.facility.repository;

import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FacilityTypeRepository extends JpaRepository<FacilityType, Long> {
    Optional<FacilityType> findByCodeIgnoreCase(String code);

    Optional<FacilityType> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    List<FacilityType> findAllByOrderByNameAsc();

    List<FacilityType> findByActiveTrueOrderByNameAsc();
}
