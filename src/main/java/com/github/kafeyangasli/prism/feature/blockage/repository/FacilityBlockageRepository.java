package com.github.kafeyangasli.prism.feature.blockage.repository;

import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacilityBlockageRepository extends JpaRepository<FacilityBlockage, Long> {

    // US-12: scheduled maintenance, active outages, and facility blockage history.
    List<FacilityBlockage> findByFacilityIdOrderByStartAtAsc(Long facilityId);

    List<FacilityBlockage> findByFacilityIdAndStatusOrderByStartAtAsc(Long facilityId, BlockageStatus status);

    List<FacilityBlockage> findByStatusOrderByStartAtAsc(BlockageStatus status);

    @Query("""
            select b from FacilityBlockage b
            where b.facility.id = :facilityId
              and b.status in :statuses
              and b.startAt < :endAt
              and (b.plannedEndAt is null or b.plannedEndAt > :startAt)
            order by b.startAt asc
            """)
    List<FacilityBlockage> findOverlapping(@Param("facilityId") Long facilityId,
                                           @Param("startAt") LocalDateTime startAt,
                                           @Param("endAt") LocalDateTime endAt,
                                           @Param("statuses") Collection<BlockageStatus> statuses);

    @Query("""
            select case when count(b) > 0 then true else false end
            from FacilityBlockage b
            where b.facility.id = :facilityId
              and b.status in :statuses
              and b.startAt < :endAt
              and (b.plannedEndAt is null or b.plannedEndAt > :startAt)
              and (:ignoredId is null or b.id <> :ignoredId)
            """)
    boolean existsOverlapping(@Param("facilityId") Long facilityId,
                              @Param("startAt") LocalDateTime startAt,
                              @Param("endAt") LocalDateTime endAt,
                              @Param("statuses") Collection<BlockageStatus> statuses,
                              @Param("ignoredId") Long ignoredId);

    @Query("""
            select b from FacilityBlockage b
            where b.status = :status
              and b.startAt <= :now
              and (b.plannedEndAt is null or b.plannedEndAt > :now)
            order by b.startAt asc
            """)
    List<FacilityBlockage> findCurrent(@Param("status") BlockageStatus status,
                                       @Param("now") LocalDateTime now);

    boolean existsByBlockageTypeId(Long blockageTypeId);

    List<FacilityBlockage> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select b from FacilityBlockage b
            join fetch b.facility
            where b.facility.id in :facilityIds
              and b.status <> :cancelledStatus
              and b.startAt < :periodEnd
              and coalesce(b.actualEndAt, b.plannedEndAt, :periodEnd) > :periodStart
            """)
    List<FacilityBlockage> findForCapacity(@Param("facilityIds") Collection<Long> facilityIds,
                                          @Param("periodStart") LocalDateTime periodStart,
                                          @Param("periodEnd") LocalDateTime periodEnd,
                                          @Param("cancelledStatus") BlockageStatus cancelledStatus);
}
