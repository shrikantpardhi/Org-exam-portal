package com.company.exam.commerce.service;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.entity.AccessGrant;
import com.company.exam.commerce.entity.AccessPackage;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.repository.AccessGrantRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AccessGrant lifecycle: manual grants, paid-grant creation/merge, revocation, and queries
 * ({@code specs/18-P9-Commerce-V1.5.md §14, §19, §20, §29.7, §64}).
 *
 * <p>AccessGrant is the authorization source of truth. Both manual and paid grants are the same
 * entity; no grant source/type column exists ({@code §64}).
 */
@Service
public class AccessGrantService {

    private final AccessGrantRepository grantRepository;
    private final CommerceCatalogService catalog;
    private final CommerceMetrics metrics;
    private final SecurityEventLogger audit;

    public AccessGrantService(AccessGrantRepository grantRepository, CommerceCatalogService catalog,
            CommerceMetrics metrics, SecurityEventLogger audit) {
        this.grantRepository = grantRepository;
        this.catalog = catalog;
        this.metrics = metrics;
        this.audit = audit;
    }

    /** A manual grant with operator-supplied dates ({@code §14.4, §19}). */
    @Transactional
    public CommerceDtos.AccessGrantResponse createManual(CommerceDtos.CreateAccessGrantRequest request, UUID actorId) {
        AccessPackage accessPackage = catalog.requirePackage(request.accessPackageId());
        AccessGrant grant = new AccessGrant();
        grant.setUserId(request.userId());
        grant.setAccessPackageId(request.accessPackageId());
        grant.setStartDate(request.startDate());
        grant.setEndDate(request.endDate());
        grant.setStatus(Status.ACTIVE);
        AccessGrant saved = grantRepository.saveAndFlush(grant);
        metrics.accessGranted();
        audit.audit(actorId, "GRANT", "AccessGrant", saved.getId(), "Created manual access grant");
        return toResponse(saved, accessPackage);
    }

    /**
     * Applies the frozen paid-grant policy ({@code §19}): start is the purchase date, end is the
     * purchase date plus the plan duration, and an existing ACTIVE grant for the same user and
     * package is extended to {@code max(existing_end, purchase_date) + duration}. A grant with a
     * null end date never expires and is never shortened.
     */
    @Transactional
    public AccessGrant applyPaidGrant(UUID userId, UUID accessPackageId, Instant purchaseDate, int durationDays,
            UUID actorId) {
        Instant purchase = purchaseDate == null ? Instant.now() : purchaseDate;
        AccessGrant grant = grantRepository
                .findFirstByUserIdAndAccessPackageIdAndStatusOrderByStartDateDesc(userId, accessPackageId,
                        Status.ACTIVE)
                .orElseGet(() -> {
                    AccessGrant created = new AccessGrant();
                    created.setUserId(userId);
                    created.setAccessPackageId(accessPackageId);
                    created.setStartDate(purchase);
                    created.setStatus(Status.ACTIVE);
                    return created;
                });
        if (grant.getId() == null) {
            grant.setEndDate(purchase.plus(durationDays, ChronoUnit.DAYS));
        } else if (grant.getEndDate() != null) {
            Instant base = grant.getEndDate().isAfter(purchase) ? grant.getEndDate() : purchase;
            grant.setEndDate(base.plus(durationDays, ChronoUnit.DAYS));
        }
        AccessGrant saved = grantRepository.saveAndFlush(grant);
        metrics.accessGranted();
        audit.audit(actorId, "GRANT", "AccessGrant", saved.getId(), "Granted access on payment success");
        return saved;
    }

    /**
     * The approved refund revocation policy ({@code §20}): every ACTIVE grant for the user and
     * package moves to INACTIVE with {@code end_date} set to the refund time. An existing earlier
     * end date is never extended.
     */
    @Transactional
    public int revokeForPackage(UUID userId, UUID accessPackageId, Instant refundTime, UUID actorId) {
        List<AccessGrant> active = grantRepository.findByUserIdAndAccessPackageIdAndStatus(userId, accessPackageId,
                Status.ACTIVE);
        for (AccessGrant grant : active) {
            grant.setStatus(Status.INACTIVE);
            if (grant.getEndDate() == null || grant.getEndDate().isAfter(refundTime)) {
                grant.setEndDate(refundTime);
            }
            AccessGrant saved = grantRepository.save(grant);
            audit.audit(actorId, "REVOKE", "AccessGrant", saved.getId(), "Revoked access on refund");
        }
        if (!active.isEmpty()) {
            metrics.accessRevoked();
        }
        return active.size();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveGrant(UUID userId, UUID accessPackageId) {
        return grantRepository.countValid(userId, accessPackageId, Instant.now()) > 0;
    }

    @Transactional(readOnly = true)
    public List<CommerceDtos.AccessGrantResponse> list(UUID userId, UUID accessPackageId, Status status,
            Boolean active) {
        List<AccessGrant> grants = userId != null ? grantRepository.findByUserId(userId) : grantRepository.findAll();
        Instant now = Instant.now();
        return grants.stream()
                .filter(grant -> accessPackageId == null || accessPackageId.equals(grant.getAccessPackageId()))
                .filter(grant -> status == null || grant.getStatus() == status)
                .filter(grant -> active == null || active == isActive(grant, now))
                .sorted(Comparator.comparing(AccessGrant::getStartDate).reversed())
                .map(grant -> toResponse(grant, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommerceDtos.AccessGrantResponse getVisible(UUID grantId, UUID userId, boolean staff) {
        AccessGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> NotFoundException.of("Access grant", grantId));
        if (!staff && !grant.getUserId().equals(userId)) {
            throw NotFoundException.of("Access grant", grantId);
        }
        return toResponse(grant, null);
    }

    private boolean isActive(AccessGrant grant, Instant now) {
        return grant.getStatus() == Status.ACTIVE && !grant.getStartDate().isAfter(now)
                && (grant.getEndDate() == null || !grant.getEndDate().isBefore(now));
    }

    private CommerceDtos.AccessGrantResponse toResponse(AccessGrant grant, AccessPackage knownPackage) {
        AccessPackage accessPackage = knownPackage != null ? knownPackage
                : catalog.requirePackage(grant.getAccessPackageId());
        return new CommerceDtos.AccessGrantResponse(grant.getId(), grant.getUserId(), grant.getAccessPackageId(),
                accessPackage.getName(), grant.getStartDate(), grant.getEndDate(),
                grant.getStatus() == null ? null : grant.getStatus().name(), isActive(grant, Instant.now()));
    }
}
