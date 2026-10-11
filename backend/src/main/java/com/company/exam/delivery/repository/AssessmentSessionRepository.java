package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.AssessmentSession;
import com.company.exam.delivery.entity.AssessmentSessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AssessmentSession} ({@code specs/14-P5-Delivery-V1.5.md §84}). */
public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {

    Optional<AssessmentSession> findByIdAndUserId(UUID id, UUID userId);

    List<AssessmentSession> findByAttemptId(UUID attemptId);

    Optional<AssessmentSession> findFirstByAttemptIdAndSessionStatusOrderByStartedOnDesc(UUID attemptId,
            AssessmentSessionStatus status);

    List<AssessmentSession> findByUserIdAndSessionStatusOrderByLastActivityOnDesc(UUID userId,
            AssessmentSessionStatus status);

    long countByAttemptId(UUID attemptId);
}
