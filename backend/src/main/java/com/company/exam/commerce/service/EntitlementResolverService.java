package com.company.exam.commerce.service;

import com.company.exam.commerce.api.AccessDecision;
import com.company.exam.commerce.api.EntitlementResolver;
import com.company.exam.commerce.entity.AccessPackageCollection;
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
 * Default {@link EntitlementResolver} ({@code specs/18-P9-Commerce-V1.5.md §3.2, §9.1, §14, §28.1, §63}).
 *
 * <p>AccessGrant is the only authorization source. Entitlement is resolved time-aware from the grant
 * (status, start inclusive, end inclusive, null end never expires) and the package-to-collection
 * mapping. A Purchase or PaymentRecord never grants access ({@code §3.1, §9.1}).
 *
 * <p>The free-versus-paid rule lives here, in the module that owns packages and grants, so that the
 * decision that gates access cannot drift from the decision that grants it (ADR-0026).
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

    /**
     * The access decision for an assessment, taken at the given server time
     * ({@code specs/18-P9-Commerce-V1.5.md §28.1}).
     *
     * <p>An assessment that no AccessPackage grants access to is free content and is allowed for every
     * authenticated user; an assessment a package grants is paid content and requires a currently valid
     * grant for one of those packages. This is the free-assessment rule of ADR-0026, derived from the
     * package-to-collection-to-assessment graph rather than from a stored flag.
     *
     * <p>Bounded read: three indexed reads (the assessment's collections, the ACTIVE package mappings
     * for those collections, and the valid-grant count over the covering packages) and no writes. The
     * decision does not scale with the number of questions.
     */
    @Override
    @Transactional(readOnly = true)
    public AccessDecision checkAssessmentAccess(UUID userId, UUID assessmentId, Instant serverNow) {
        if (userId == null || assessmentId == null || serverNow == null) {
            metrics.authorization(false);
            return AccessDecision.deny(AccessDecision.Reason.NO_ACTIVE_GRANT);
        }
        List<UUID> packageIds = packagesGrantingAccess(assessmentId);
        if (packageIds.isEmpty()) {
            // No AccessPackage grants this assessment, so it is free content (§16, ADR-0026).
            metrics.authorization(true);
            return AccessDecision.allow(AccessDecision.Reason.FREE_ASSESSMENT);
        }
        boolean granted = grantRepository.countValidForPackages(userId, packageIds, serverNow) > 0;
        metrics.authorization(granted);
        return granted
                ? AccessDecision.allow(AccessDecision.Reason.ACTIVE_GRANT)
                : AccessDecision.deny(AccessDecision.Reason.NO_ACTIVE_GRANT);
    }

    /**
     * Whether the user may access the assessment ({@code §9.1}). It never infers entitlement from a
     * purchase or a payment record.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean canAccessAssessment(UUID userId, UUID assessmentId) {
        return checkAssessmentAccess(userId, assessmentId, Instant.now()).allowed();
    }

    /**
     * The ACTIVE packages that grant access to the assessment, resolved through the assessment's
     * collections. Empty means the assessment is free content (ADR-0026).
     */
    private List<UUID> packagesGrantingAccess(UUID assessmentId) {
        List<UUID> collectionIds = assessmentCollections.collectionIdsForAssessment(assessmentId);
        if (collectionIds.isEmpty()) {
            return List.of();
        }
        return mappingRepository.findByAssessmentCollectionIdInAndStatus(collectionIds, Status.ACTIVE).stream()
                .map(AccessPackageCollection::getAccessPackageId)
                .distinct()
                .toList();
    }
}
