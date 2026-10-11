package com.company.exam.delivery.repository;

import com.company.exam.delivery.entity.AttemptQuestionOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the immutable {@link AttemptQuestionOption} ordering snapshot ({@code §85}). */
public interface AttemptQuestionOptionRepository extends JpaRepository<AttemptQuestionOption, UUID> {

    List<AttemptQuestionOption> findByAttemptQuestionIdOrderByDisplayOrderAsc(UUID attemptQuestionId);

    List<AttemptQuestionOption> findByAttemptQuestionIdInOrderByDisplayOrderAsc(Collection<UUID> attemptQuestionIds);

    long countByAttemptQuestionId(UUID attemptQuestionId);
}
