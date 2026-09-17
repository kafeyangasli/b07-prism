package com.github.kafeyangasli.prism.feature.report.repository;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.user.model.User;

import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // US-06 and US-07: submit and track a user's reports.
    List<Report> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Report> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReportStatus status);

    // Staff/Admin queue and facility issue history.
    List<Report> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);

    List<Report> findByFacilityIdAndStatusOrderByCreatedAtAsc(Long facilityId, ReportStatus status);

    List<Report> findByStatusInOrderByCreatedAtAsc(Collection<ReportStatus> statuses);

    List<Report> findByHandledByIdOrderByHandledAtDesc(Long userId);
}
