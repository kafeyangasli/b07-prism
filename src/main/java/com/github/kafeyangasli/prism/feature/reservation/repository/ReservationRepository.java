package com.github.kafeyangasli.prism.feature.reservation.repository;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.user.model.User;

import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // US-04 and US-05: a user's own reservation history and status.
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Reservation> findByUserIdAndStatusOrderByStartAtDesc(Long userId, ReservationStatus status);

    Optional<Reservation> findByIdAndUserId(Long id, Long userId);

    // Staff/Admin dashboard and approval queue.
    List<Reservation> findByStatusOrderByCreatedAtAsc(ReservationStatus status);

    @Query("""
            select r from Reservation r
            where r.status = :status
              and (r.expiresAt is null or r.expiresAt > :now)
            order by r.createdAt asc
            """)
    List<Reservation> findApprovalQueue(@Param("status") ReservationStatus status,
                                       @Param("now") LocalDateTime now);

    // Conflict detection after the facility and user rows have been locked.
    @Query("""
            select r from Reservation r
            where r.facility.id = :facilityId
              and r.status in :statuses
              and (:endAt is null or r.startAt < :endAt)
              and r.endAt > :startAt
            order by r.startAt asc
            """)
    List<Reservation> findOverlapping(@Param("facilityId") Long facilityId,
                                      @Param("startAt") LocalDateTime startAt,
                                      @Param("endAt") LocalDateTime endAt,
                                      @Param("statuses") Collection<ReservationStatus> statuses);

    @Query("""
            select case when count(r) > 0 then true else false end
            from Reservation r
            where r.facility.id = :facilityId
              and r.status in :statuses
              and (:endAt is null or r.startAt < :endAt)
              and r.endAt > :startAt
              and (:ignoredId is null or r.id <> :ignoredId)
            """)
    boolean existsOverlapping(@Param("facilityId") Long facilityId,
                              @Param("startAt") LocalDateTime startAt,
                              @Param("endAt") LocalDateTime endAt,
                              @Param("statuses") Collection<ReservationStatus> statuses,
                              @Param("ignoredId") Long ignoredId);

    // Seven-reservation policy and expiry processing.
    @Query("""
            select count(r) from Reservation r
            where r.user.id = :userId and r.status in :statuses
            """)
    long countByUserAndStatuses(@Param("userId") Long userId,
                                @Param("statuses") Collection<ReservationStatus> statuses);

    @Query("""
            select r from Reservation r
            where r.status = :status and r.expiresAt is not null and r.expiresAt <= :now
            order by r.expiresAt asc
            """)
    List<Reservation> findExpiredPending(@Param("status") ReservationStatus status,
                                         @Param("now") LocalDateTime now);

    List<Reservation> findByFacilityIdAndStatusOrderByStartAtAsc(Long facilityId, ReservationStatus status);

    List<Reservation> findByProcessedByIdOrderByProcessedAtDesc(Long userId);
}
