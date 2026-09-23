package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class FacilityServiceImpl implements FacilityService {

    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final FacilityBlockageRepository facilityBlockageRepository;

    public FacilityServiceImpl(FacilityRepository facilityRepository,
                               UserRepository userRepository,
                               ReservationRepository reservationRepository,
                               FacilityBlockageRepository facilityBlockageRepository) {
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.facilityBlockageRepository = facilityBlockageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Facility> getPublicCatalogue() {
        return facilityRepository.findByAdministrativeStatusOrderByNameAsc(AdministrativeStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Facility> searchCatalogue(String type, String location, Integer capacity) {
        String t = (type != null && !type.isBlank()) ? type.trim() : null;
        String l = (location != null && !location.isBlank()) ? location.trim() : null;
        return facilityRepository.searchCatalogue(AdministrativeStatus.ACTIVE, t, l, capacity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Facility> getAvailableFacilities(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessRuleException("Valid startAt and endAt interval is required for availability check.");
        }
        return facilityRepository.findAvailableFacilities(
                AdministrativeStatus.ACTIVE,
                startAt,
                endAt,
                List.of(ReservationStatus.APPROVED),
                List.of(BlockageStatus.ACTIVE, BlockageStatus.SCHEDULED)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Facility> getAllFacilities() {
        return facilityRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Facility getFacilityById(Long id) {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + id));
    }

    @Override
    public Facility createFacility(Long adminId, FacilityDto dto) {
        validateAdmin(adminId);
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BusinessRuleException("Facility code is required.");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessRuleException("Facility name is required.");
        }
        if (dto.getType() == null || dto.getType().isBlank()) {
            throw new BusinessRuleException("Facility type is required.");
        }
        if (dto.getLocation() == null || dto.getLocation().isBlank()) {
            throw new BusinessRuleException("Facility location is required.");
        }
        if (dto.getCapacity() == null || dto.getCapacity() <= 0) {
            throw new BusinessRuleException("Facility capacity must be positive.");
        }

        String normalizedCode = dto.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (facilityRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleException("Facility code already exists.");
        }

        AdministrativeStatus status = dto.getAdministrativeStatus() != null ? dto.getAdministrativeStatus() : AdministrativeStatus.ACTIVE;
        Facility facility = new Facility(normalizedCode, dto.getName().trim(), dto.getType().trim(), dto.getLocation().trim(), dto.getCapacity(), dto.getDescription(), status);
        return facilityRepository.save(facility);
    }

    @Override
    public Facility updateFacility(Long adminId, Long facilityId, FacilityDto dto) {
        validateAdmin(adminId);
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + facilityId));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            facility.setName(dto.getName().trim());
        }
        if (dto.getType() != null && !dto.getType().isBlank()) {
            facility.setType(dto.getType().trim());
        }
        if (dto.getLocation() != null && !dto.getLocation().isBlank()) {
            facility.setLocation(dto.getLocation().trim());
        }
        if (dto.getCapacity() != null) {
            if (dto.getCapacity() <= 0) {
                throw new BusinessRuleException("Facility capacity must be positive.");
            }
            facility.setCapacity(dto.getCapacity());
        }
        if (dto.getDescription() != null) {
            facility.setDescription(dto.getDescription());
        }
        if (dto.getAdministrativeStatus() != null) {
            facility.setAdministrativeStatus(dto.getAdministrativeStatus());
        }

        return facilityRepository.save(facility);
    }

    @Override
    public Facility deactivateFacility(Long adminId, Long facilityId) {
        validateAdmin(adminId);
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + facilityId));

        facility.setAdministrativeStatus(AdministrativeStatus.INACTIVE);
        return facilityRepository.save(facility);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> getApprovedReservationsForFacility(Long facilityId) {
        return reservationRepository.findByFacilityIdAndStatusOrderByStartAtAsc(facilityId, ReservationStatus.APPROVED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacilityBlockage> getBlockagesForFacility(Long facilityId) {
        return facilityBlockageRepository.findByFacilityIdOrderByStartAtAsc(facilityId);
    }

    private void validateAdmin(Long adminId) {
        if (adminId == null) {
            throw new BusinessRuleException("Admin ID is required.");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("Only Admin can perform this action.");
        }
    }
}
