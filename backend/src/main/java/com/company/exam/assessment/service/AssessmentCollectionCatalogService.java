package com.company.exam.assessment.service;

import com.company.exam.assessment.api.AssessmentCollectionCatalog;
import com.company.exam.assessment.repository.AssessmentCollectionRepository;
import com.company.exam.assessment.repository.CollectionAssessmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link AssessmentCollectionCatalog} ({@code specs/18-P9-Commerce-V1.5.md §9.1}). */
@Service
public class AssessmentCollectionCatalogService implements AssessmentCollectionCatalog {

    private final CollectionAssessmentRepository collectionAssessmentRepository;
    private final AssessmentCollectionRepository collectionRepository;

    public AssessmentCollectionCatalogService(CollectionAssessmentRepository collectionAssessmentRepository,
            AssessmentCollectionRepository collectionRepository) {
        this.collectionAssessmentRepository = collectionAssessmentRepository;
        this.collectionRepository = collectionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> collectionIdsForAssessment(UUID assessmentId) {
        return collectionAssessmentRepository.findByAssessmentId(assessmentId).stream()
                .map(com.company.exam.assessment.entity.CollectionAssessment::getCollectionId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean collectionExists(UUID assessmentCollectionId) {
        return collectionRepository.findById(assessmentCollectionId).isPresent();
    }
}
