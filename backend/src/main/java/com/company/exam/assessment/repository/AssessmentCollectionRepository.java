package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.AssessmentCollection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AssessmentCollection} ({@code §5.8, §76}). */
public interface AssessmentCollectionRepository extends JpaRepository<AssessmentCollection, UUID> {

    Optional<AssessmentCollection> findByCode(String code);

    boolean existsByCode(String code);
}
