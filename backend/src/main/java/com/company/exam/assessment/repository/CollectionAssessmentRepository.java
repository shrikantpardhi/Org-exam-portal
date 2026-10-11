package com.company.exam.assessment.repository;

import com.company.exam.assessment.entity.CollectionAssessment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link CollectionAssessment} ({@code §5.9, §77}). */
public interface CollectionAssessmentRepository extends JpaRepository<CollectionAssessment, UUID> {

    List<CollectionAssessment> findByCollectionId(UUID collectionId);

    Optional<CollectionAssessment> findByCollectionIdAndAssessmentId(UUID collectionId, UUID assessmentId);

    boolean existsByCollectionIdAndAssessmentId(UUID collectionId, UUID assessmentId);
}
