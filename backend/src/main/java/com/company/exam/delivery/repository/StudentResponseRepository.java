package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.StudentResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link StudentResponse} ({@code §86}). */
public interface StudentResponseRepository extends JpaRepository<StudentResponse, UUID> {

    Optional<StudentResponse> findByAttemptIdAndQuestionVersionId(UUID attemptId, UUID questionVersionId);

    List<StudentResponse> findByAttemptId(UUID attemptId);

    long countByAttemptId(UUID attemptId);
}
