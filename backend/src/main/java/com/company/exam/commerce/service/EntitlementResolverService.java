package com.company.exam.commerce.service;

import com.company.exam.commerce.api.EntitlementResolver;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.repository.AccessGrantRepository;
import com.company.exam.commerce.repository.AccessPackageCollectionRepository;
import com.company.exam.assessment.api.AssessmentCollectionCatalog;
import com.company.exam.common.enums.Status;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link EntitlementResolver} ({@code specs/18-P9-Commerce-V1.5.md §3.2, §9.1, §14, §63}).
 *
 * <p>AccessGrant is the only authorization source. Entitlement is resolved time-aware from the grant
 * (status, start inclusive, end inclusive, null end never expires) and the package-to-collection
 * mapping. A Purchase or PaymentRecord never grants access ({@code §3.1, §9.1}).
 */
@Service
public class EntitlementResolverService implements EntitlementResolver {

    private final AccessGrantRepository grantRepository;
    private final AccessPackageCollectionRepository mappingRepository;
    private final AssessmentCollectionCatalog assessmentCollections;
    private final CommerceMetrics metrics;

    public EntitlementResolverService(AccessGrantRepository grantRepository,
            AccessPackageCollectionRepository mappingRepository,
            AssessmentCollectionCatalog assessmentCollections, CommerceMetrics metrics) {
        this.grantRepository = grantRepository;
        this.mappingRepository = mappingRepository;
        this.assessmentCollections = assessmentCollections;
        this.metrics = metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveGrant(UUID userId, UUID accessPackageId) {
        boolean allowed = accessPackageId != null && userId != null
                && grantRepository.countValid(userId, accessPackageId, Instant.now()) > 0;
        metrics.authorization(allowed);
        return allowed;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessAssessment(UUID userId, UUID assessmentId) {
        List<UUID> collectionIds = assessmentCollections.collectionIdsForAssessment(assessmentId);
        if (collectionIds.isEmpty()) {
            metrics.authorization(false);
            return false;
        }
        List<UUID> packageIds = mappingRepository
                .findByAssessmentCollectionIdInAndStatus(collectionIds, Status.ACTIVE).stream()
                .map(com.company.exam.commerce.entity.AccessPackageCollection::getAccessPackageId)
                .distinct()
                .toList();
        boolean allowed = !packageIds.isEmpty()
                && grantRepository.countValidForPackages(userId, packageIds, Instant.now()) > 0;
        metrics.authorization(allowed);
        return allowed;
    }
}
