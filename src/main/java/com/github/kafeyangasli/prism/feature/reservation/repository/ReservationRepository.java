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
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    interface ReservationLockContext {
        Long getFacilityId();
        Long getUserId();
    }

    @Query("select r.facility.id as facilityId, r.user.id as userId from Reservation r where r.id = :id")
    Optional<ReservationLockContext> findLockContextById(@Param("id") Long id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r join fetch r.facility join fetch r.user where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

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

    @Query("""
            select r from Reservation r
            join fetch r.facility
            join fetch r.user
            where r.status = :status
              and (r.expiresAt is null or r.expiresAt > :now)
              and r.startAt > :now
            order by
              case when :sort = 'start' then r.startAt else r.createdAt end asc,
              r.id asc
            """)
    List<Reservation> findProcessableQueue(@Param("status") ReservationStatus status,
                                           @Param("now") LocalDateTime now,
                                           @Param("sort") String sort);

    // Conflict detection after the facility and user rows have been locked.
    @Query("""
            select r from Reservation r
            where r.facility.id = :facilityId
              and r.status in :statuses
              and r.startAt < :endAt
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
              and r.startAt < :endAt
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
            select count(r) from Reservation r
            where r.user.id = :userId
              and r.status = :status
              and r.endAt > :now
            """)
    long countActiveApproved(@Param("userId") Long userId,
                             @Param("status") ReservationStatus status,
                             @Param("now") LocalDateTime now);

    @Query("""
            select r from Reservation r
            join fetch r.facility
            where r.status in :statuses
              and r.startAt < :periodEnd
              and r.endAt > :periodStart
              and r.facility.id in :facilityIds
            """)
    List<Reservation> findForOccupancy(@Param("statuses") Collection<ReservationStatus> statuses,
                                       @Param("periodStart") LocalDateTime periodStart,
                                       @Param("periodEnd") LocalDateTime periodEnd,
                                       @Param("facilityIds") Collection<Long> facilityIds);

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
