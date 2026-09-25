package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;

import java.time.LocalDateTime;
import java.util.List;

public interface FacilityService {
    List<Facility> getPublicCatalogue();

    List<Facility> searchCatalogue(String type, String location, Integer capacity);

    List<Facility> getAvailableFacilities(LocalDateTime startAt, LocalDateTime endAt);

    List<Facility> getAllFacilities();

    Facility getFacilityById(Long id);

    Facility createFacility(Long adminId, FacilityDto dto);

    Facility updateFacility(Long adminId, Long facilityId, FacilityDto dto);

    Facility activateFacility(Long adminId, Long facilityId);

    Facility deactivateFacility(Long adminId, Long facilityId);

    List<com.github.kafeyangasli.prism.feature.reservation.model.Reservation> getApprovedReservationsForFacility(Long facilityId);

    List<com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage> getBlockagesForFacility(Long facilityId);
}
