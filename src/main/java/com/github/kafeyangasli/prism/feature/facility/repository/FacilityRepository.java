package com.github.kafeyangasli.prism.feature.facility.repository;

import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    // Public facility catalogue and admin facility management.
    Optional<Facility> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Facility> findByAdministrativeStatusOrderByNameAsc(AdministrativeStatus status);

    @Query("""
            select f from Facility f
            where f.administrativeStatus = :status
              and (:type is null or lower(f.type) = lower(:type))
              and (:location is null or lower(f.location) like lower(concat('%', :location, '%')))
              and (:minimumCapacity is null or f.capacity >= :minimumCapacity)
            order by f.name asc
            """)
    List<Facility> searchCatalogue(@Param("status") AdministrativeStatus status,
                                   @Param("type") String type,
                                   @Param("location") String location,
                                   @Param("minimumCapacity") Integer minimumCapacity);

    // Availability uses half-open intervals: [startAt, endAt).
    @Query("""
            select f from Facility f
            where f.administrativeStatus = :activeStatus
              and not exists (
                  select r.id from Reservation r
                  where r.facility = f
                    and r.status in :reservationStatuses
                    and r.startAt < :endAt
                    and r.endAt > :startAt
              )
              and not exists (
                  select b.id from FacilityBlockage b
                  where b.facility = f
                    and b.status in :blockageStatuses
                    and b.startAt < :endAt
                    and (b.plannedEndAt is null or b.plannedEndAt > :startAt)
              )
            order by f.name asc
            """)
    List<Facility> findAvailableFacilities(@Param("activeStatus") AdministrativeStatus activeStatus,
                                           @Param("startAt") LocalDateTime startAt,
                                           @Param("endAt") LocalDateTime endAt,
                                           @Param("reservationStatuses") Collection<ReservationStatus> reservationStatuses,
                                           @Param("blockageStatuses") Collection<BlockageStatus> blockageStatuses);

    // Used by approval and blockage mutation flows. Callers must acquire this lock first.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Facility f where f.id = :id")
    Optional<Facility> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select f from Facility f
            where (:facilityId is null or f.id = :facilityId)
              and (:type is null or lower(f.type) = lower(:type))
              and (:location is null or lower(f.location) like lower(concat('%', :location, '%')))
            order by f.name asc
            """)
    List<Facility> findForRecap(@Param("facilityId") Long facilityId,
                                @Param("type") String type,
                                @Param("location") String location);
}
