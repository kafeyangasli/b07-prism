package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
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

    private final com.github.kafeyangasli.prism.feature.reservation.service.EquipmentAvailabilityService equipmentAvailability;
    private final FacilityRepository facilityRepository;
    private final FacilityTypeRepository facilityTypeRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final FacilityBlockageRepository facilityBlockageRepository;
    private final java.time.Clock clock;

    public FacilityServiceImpl(FacilityRepository facilityRepository,
                               FacilityTypeRepository facilityTypeRepository,
                               UserRepository userRepository,
                               ReservationRepository reservationRepository,
                               FacilityBlockageRepository facilityBlockageRepository,
                               com.github.kafeyangasli.prism.feature.reservation.service.EquipmentAvailabilityService equipmentAvailability,
                               java.time.Clock clock) {
        this.facilityRepository = facilityRepository;
        this.equipmentAvailability = equipmentAvailability;
        this.facilityTypeRepository = facilityTypeRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.facilityBlockageRepository = facilityBlockageRepository;
        this.clock = clock;
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
    public List<Facility> searchCatalogueSelections(List<String> types, List<String> locations, Integer capacity) {
        var selectedTypes = FacilityFilters.normalize(types);
        var selectedLocations = FacilityFilters.normalize(locations);
        return facilityRepository.searchCatalogue(AdministrativeStatus.ACTIVE, null, null, capacity).stream()
                .filter(f -> FacilityFilters.matches(f, selectedTypes, selectedLocations)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Facility> getAvailableFacilities(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new BusinessRuleException("Waktu mulai dan selesai yang valid wajib diisi untuk memeriksa ketersediaan.");
        }
        if (com.github.kafeyangasli.prism.shared.util.OperatingDays.includesWeekend(startAt, endAt)) return List.of();
        List<Facility> exclusive = facilityRepository.findAvailableFacilities(
                AdministrativeStatus.ACTIVE,
                startAt,
                endAt,
                List.of(ReservationStatus.APPROVED),
                List.of(BlockageStatus.ACTIVE, BlockageStatus.SCHEDULED)
        );
        var result = new java.util.ArrayList<>(exclusive.stream().filter(f -> !f.isEquipment()).toList());
        facilityRepository.findByAdministrativeStatusOrderByNameAsc(AdministrativeStatus.ACTIVE).stream()
                .filter(Facility::isEquipment)
                .filter(f -> equipmentAvailability.available(f, startAt, endAt) > 0).forEach(result::add);
        result.sort(java.util.Comparator.comparing(Facility::getName));
        return result;
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
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas dengan ID " + id + " tidak ditemukan."));
    }

    @Override
    public Facility createFacility(Long adminId, FacilityDto dto) {
        validateAdmin(adminId);
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BusinessRuleException("Kode fasilitas wajib diisi.");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessRuleException("Nama fasilitas wajib diisi.");
        }
        FacilityType facilityType = requireAssignableType(dto.getFacilityTypeId());
        if (dto.getLocation() == null || dto.getLocation().isBlank()) {
            throw new BusinessRuleException("Lokasi fasilitas wajib diisi.");
        }
        if (dto.getCapacity() == null || dto.getCapacity() <= 0) {
            throw new BusinessRuleException("Kapasitas fasilitas harus lebih dari nol.");
        }

        String normalizedCode = dto.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (facilityRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleException("Kode fasilitas sudah digunakan.");
        }

        AdministrativeStatus status = dto.getAdministrativeStatus() != null ? dto.getAdministrativeStatus() : AdministrativeStatus.ACTIVE;
        Facility facility = new Facility(normalizedCode, dto.getName().trim(), facilityType, dto.getLocation().trim(), dto.getCapacity(), dto.getDescription(), status);
        validateStock(facilityType, dto.getTotalStock());
        facility.setTotalStock(dto.getTotalStock());
        facility.recordCreationStatus(LocalDateTime.now(clock));
        return facilityRepository.save(facility);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Facility updateFacility(Long adminId, Long facilityId, FacilityDto dto) {
        validateAdmin(adminId);
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas dengan ID " + facilityId + " tidak ditemukan."));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            facility.setName(dto.getName().trim());
        }
        if (dto.getFacilityTypeId() == null) {
            throw new BusinessRuleException("Tipe fasilitas wajib dipilih.");
        }
        Long currentTypeId = facility.getFacilityType().getId();
        if (!dto.getFacilityTypeId().equals(currentTypeId)) {
            FacilityType nextType = requireAssignableType(dto.getFacilityTypeId());
            if (facility.isEquipment() != nextType.isQuantityBased() && reservationRepository.existsByFacilityId(facilityId)) {
                throw new BusinessRuleException("Perubahan antara Alat dan tipe lain tidak diizinkan setelah fasilitas memiliki riwayat reservasi.");
            }
            facility.setFacilityType(nextType);
        }
        validateStock(facility.getFacilityType(), dto.getTotalStock());
        // Queries may auto-flush: keep the new type and its stock valid together.
        // The facility lock remains held; a failed peak check rolls this assignment back.
        facility.setTotalStock(dto.getTotalStock());
        if (facility.isEquipment() && dto.getTotalStock() < equipmentAvailability.maximumFutureAllocated(facility)) {
            throw new BusinessRuleException("Stok tidak boleh lebih kecil dari jumlah alat yang dialokasikan secara bersamaan.");
        }
        if (dto.getLocation() != null && !dto.getLocation().isBlank()) {
            facility.setLocation(dto.getLocation().trim());
        }
        if (dto.getCapacity() != null) {
            if (dto.getCapacity() <= 0) {
                throw new BusinessRuleException("Kapasitas fasilitas harus lebih dari nol.");
            }
            facility.setCapacity(dto.getCapacity());
        }
        if (dto.getDescription() != null) {
            facility.setDescription(dto.getDescription());
        }
        if (dto.getAdministrativeStatus() != null) {
            facility.changeAdministrativeStatus(dto.getAdministrativeStatus(), LocalDateTime.now(clock));
        }

        return facilityRepository.save(facility);
    }

    @Override
    public Facility activateFacility(Long adminId, Long facilityId) {
        validateAdmin(adminId);
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas dengan ID " + facilityId + " tidak ditemukan."));

        facility.changeAdministrativeStatus(AdministrativeStatus.ACTIVE, LocalDateTime.now(clock));
        return facilityRepository.save(facility);
    }

    @Override
    public Facility deactivateFacility(Long adminId, Long facilityId) {
        validateAdmin(adminId);
        Facility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas dengan ID " + facilityId + " tidak ditemukan."));

        facility.changeAdministrativeStatus(AdministrativeStatus.INACTIVE, LocalDateTime.now(clock));
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

    private void validateStock(FacilityType type, Integer stock) {
        if (type.isQuantityBased() ? stock == null || stock <= 0 : stock != null) {
            throw new BusinessRuleException("Stok positif wajib untuk Alat; kosongkan stok untuk fasilitas lain.");
        }
    }

    private void validateAdmin(Long adminId) {
        if (adminId == null) {
            throw new BusinessRuleException("ID admin wajib tersedia.");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin dengan ID " + adminId + " tidak ditemukan."));
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("Tindakan ini hanya dapat dilakukan oleh Admin.");
        }
    }

    private FacilityType requireAssignableType(Long facilityTypeId) {
        if (facilityTypeId == null) {
            throw new BusinessRuleException("Tipe fasilitas wajib dipilih.");
        }
        FacilityType facilityType = facilityTypeRepository.findById(facilityTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tipe fasilitas dengan ID " + facilityTypeId + " tidak ditemukan."));
        if (!facilityType.isActive()) {
            throw new BusinessRuleException("Tipe fasilitas yang tidak aktif tidak dapat dipilih.");
        }
        return facilityType;
    }
}
