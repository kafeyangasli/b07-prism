package com.github.kafeyangasli.prism.feature.report.repository;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.user.model.User;

import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // US-06 and US-07: submit and track a user's reports.
    List<Report> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Report> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReportStatus status);

    // Staff/Admin queue and facility issue history.
    List<Report> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);

    List<Report> findByFacilityIdAndStatusOrderByCreatedAtAsc(Long facilityId, ReportStatus status);

    List<Report> findByStatusInOrderByCreatedAtAsc(Collection<ReportStatus> statuses);

    @Query("""
            select r from Report r
            join fetch r.facility
            join fetch r.user
            where r.status in :statuses
            order by r.createdAt asc
            """)
    List<Report> findUnresolvedQueue(@Param("statuses") Collection<ReportStatus> statuses);

    interface FacilityIssueCount {
        Long getFacilityId();
        long getIssueCount();
    }

    @Query("""
            select r.facility.id as facilityId, count(r) as issueCount
            from Report r
            where r.facility.id in :facilityIds
              and r.createdAt >= :periodStart
              and r.createdAt < :periodEnd
            group by r.facility.id
            """)
    List<FacilityIssueCount> countIssuesByFacility(@Param("facilityIds") Collection<Long> facilityIds,
                                                   @Param("periodStart") java.time.LocalDateTime periodStart,
                                                   @Param("periodEnd") java.time.LocalDateTime periodEnd);

    List<Report> findByHandledByIdOrderByHandledAtDesc(Long userId);
}
