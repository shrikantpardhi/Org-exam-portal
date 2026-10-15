package com.company.exam.commerce.service;

import com.company.exam.commerce.dto.CommerceDtos;
import com.company.exam.commerce.entity.AccessPackage;
import com.company.exam.commerce.entity.AccessPackageCollection;
import com.company.exam.commerce.entity.Coupon;
import com.company.exam.commerce.entity.SubscriptionPlan;
import com.company.exam.commerce.observability.CommerceMetrics;
import com.company.exam.commerce.pricing.PricingCalculator;
import com.company.exam.commerce.repository.AccessPackageCollectionRepository;
import com.company.exam.commerce.repository.AccessPackageRepository;
import com.company.exam.commerce.repository.CouponRepository;
import com.company.exam.commerce.repository.SubscriptionPlanRepository;
import com.company.exam.assessment.api.AssessmentCollectionCatalog;
import com.company.exam.common.api.ApiError;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.BusinessException;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Commerce catalog: packages, package-to-collection mapping, subscription plans, coupons, and
 * coupon validation ({@code specs/18-P9-Commerce-V1.5.md §8-§10, §15, §29.1-§29.3, §29.8, §33}).
 */
@Service
public class CommerceCatalogService {

    private final AccessPackageRepository packageRepository;
    private final AccessPackageCollectionRepository mappingRepository;
    private final SubscriptionPlanRepository planRepository;
    private final CouponRepository couponRepository;
    private final AssessmentCollectionCatalog assessmentCollections;
    private final PricingCalculator pricingCalculator;
    private final CommerceMetrics metrics;
    private final SecurityEventLogger audit;

    public CommerceCatalogService(AccessPackageRepository packageRepository,
            AccessPackageCollectionRepository mappingRepository, SubscriptionPlanRepository planRepository,
            CouponRepository couponRepository, AssessmentCollectionCatalog assessmentCollections,
            PricingCalculator pricingCalculator, CommerceMetrics metrics, SecurityEventLogger audit) {
        this.packageRepository = packageRepository;
        this.mappingRepository = mappingRepository;
        this.planRepository = planRepository;
        this.couponRepository = couponRepository;
        this.assessmentCollections = assessmentCollections;
        this.pricingCalculator = pricingCalculator;
        this.metrics = metrics;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- access packages

    @Transactional(readOnly = true)
    public List<CommerceDtos.AccessPackageResponse> listPackages(Status status) {
        List<AccessPackage> packages = status == null ? packageRepository.findAll()
                : packageRepository.findByStatus(status);
        return packages.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CommerceDtos.AccessPackageResponse getPackage(UUID id) {
        return toResponse(requirePackage(id));
    }

    @Transactional
    public CommerceDtos.AccessPackageResponse createPackage(CommerceDtos.CreateAccessPackageRequest request,
            UUID actorId) {
        AccessPackage entity = new AccessPackage();
        entity.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.name().trim());
        entity.setDescription(request.description());
        try {
            AccessPackage saved = packageRepository.saveAndFlush(entity);
            audit.audit(actorId, "CREATE", "AccessPackage", saved.getId(), "Created access package");
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("An access package with that code already exists");
        }
    }

    @Transactional
    public CommerceDtos.AccessPackageResponse updatePackage(UUID id, CommerceDtos.UpdateAccessPackageRequest request,
            UUID actorId) {
        AccessPackage entity = requirePackage(id);
        if (request.name() != null) {
            entity.setName(request.name().trim());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        AccessPackage saved = packageRepository.save(entity);
        audit.audit(actorId, "UPDATE", "AccessPackage", saved.getId(), "Updated access package");
        return toResponse(saved);
    }

    /** Archives a package; historical purchases and grants stay queryable ({@code §8}). */
    @Transactional
    public void archivePackage(UUID id, UUID actorId) {
        AccessPackage entity = requirePackage(id);
        entity.setStatus(Status.ARCHIVED);
        packageRepository.save(entity);
        audit.audit(actorId, "ARCHIVE", "AccessPackage", entity.getId(), "Archived access package");
    }

    // ---------------------------------------------------------------- package mapping

    @Transactional(readOnly = true)
    public List<CommerceDtos.PackageCollectionResponse> listPackageCollections(UUID packageId) {
        requirePackage(packageId);
        return mappingRepository.findByAccessPackageId(packageId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CommerceDtos.PackageCollectionResponse addPackageCollection(UUID packageId, UUID collectionId,
            UUID actorId) {
        requirePackage(packageId);
        if (!assessmentCollections.collectionExists(collectionId)) {
            throw new BusinessException("Assessment collection not found",
                    List.of(ApiError.of("collectionId", "COLLECTION_NOT_FOUND",
                            "The assessment collection does not exist")));
        }
        AccessPackageCollection mapping = new AccessPackageCollection();
        mapping.setAccessPackageId(packageId);
        mapping.setAssessmentCollectionId(collectionId);
        try {
            AccessPackageCollection saved = mappingRepository.saveAndFlush(mapping);
            audit.audit(actorId, "MAP", "AccessPackageCollection", saved.getId(),
                    "Mapped collection to access package");
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("That collection is already mapped to this package");
        }
    }

    @Transactional
    public void removePackageCollection(UUID packageId, UUID collectionId, UUID actorId) {
        requirePackage(packageId);
        AccessPackageCollection mapping = mappingRepository
                .findByAccessPackageIdAndAssessmentCollectionId(packageId, collectionId)
                .orElseThrow(() -> NotFoundException.of("Package collection mapping", collectionId));
        mappingRepository.delete(mapping);
        audit.audit(actorId, "UNMAP", "AccessPackageCollection", mapping.getId(),
                "Removed collection mapping from access package");
    }

    // ---------------------------------------------------------------- subscription plans

    @Transactional(readOnly = true)
    public List<CommerceDtos.SubscriptionPlanResponse> listPlans(UUID accessPackageId, Status status) {
        List<SubscriptionPlan> plans = accessPackageId != null ? planRepository.findByAccessPackageId(accessPackageId)
                : planRepository.findAll();
        return plans.stream()
                .filter(plan -> status == null || plan.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommerceDtos.SubscriptionPlanResponse getPlan(UUID id) {
        return toResponse(requirePlan(id));
    }

    @Transactional
    public CommerceDtos.SubscriptionPlanResponse createPlan(CommerceDtos.CreateSubscriptionPlanRequest request,
            UUID actorId) {
        requirePackage(request.accessPackageId());
        SubscriptionPlan entity = new SubscriptionPlan();
        entity.setAccessPackageId(request.accessPackageId());
        entity.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.name().trim());
        entity.setDurationDays(request.durationDays());
        entity.setPrice(request.price());
        try {
            SubscriptionPlan saved = planRepository.saveAndFlush(entity);
            audit.audit(actorId, "CREATE", "SubscriptionPlan", saved.getId(), "Created subscription plan");
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("A subscription plan with that code already exists");
        }
    }

    @Transactional
    public CommerceDtos.SubscriptionPlanResponse updatePlan(UUID id, CommerceDtos.UpdateSubscriptionPlanRequest request,
            UUID actorId) {
        SubscriptionPlan entity = requirePlan(id);
        if (request.name() != null) {
            entity.setName(request.name().trim());
        }
        if (request.durationDays() != null) {
            entity.setDurationDays(request.durationDays());
        }
        if (request.price() != null) {
            entity.setPrice(request.price());
        }
        SubscriptionPlan saved = planRepository.save(entity);
        audit.audit(actorId, "UPDATE", "SubscriptionPlan", saved.getId(), "Updated subscription plan");
        return toResponse(saved);
    }

    @Transactional
    public void archivePlan(UUID id, UUID actorId) {
        SubscriptionPlan entity = requirePlan(id);
        entity.setStatus(Status.ARCHIVED);
        planRepository.save(entity);
        audit.audit(actorId, "ARCHIVE", "SubscriptionPlan", entity.getId(), "Archived subscription plan");
    }

    // ---------------------------------------------------------------- coupons

    @Transactional(readOnly = true)
    public List<CommerceDtos.CouponResponse> listCoupons(Status status) {
        return couponRepository.findAll().stream()
                .filter(coupon -> status == null || coupon.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommerceDtos.CouponResponse getCoupon(UUID id) {
        return toResponse(couponRepository.findById(id).orElseThrow(() -> NotFoundException.of("Coupon", id)));
    }

    @Transactional
    public CommerceDtos.CouponResponse createCoupon(CommerceDtos.CreateCouponRequest request, UUID actorId) {
        Coupon entity = new Coupon();
        entity.setCode(normalizeCode(request.code()));
        entity.setName(request.name().trim());
        entity.setDiscountValue(request.discountValue());
        entity.setValidFrom(request.validFrom());
        entity.setValidTo(request.validTo());
        try {
            Coupon saved = couponRepository.saveAndFlush(entity);
            audit.audit(actorId, "CREATE", "Coupon", saved.getId(), "Created coupon");
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("A coupon with that code already exists");
        }
    }

    @Transactional
    public CommerceDtos.CouponResponse updateCoupon(UUID id, CommerceDtos.UpdateCouponRequest request, UUID actorId) {
        Coupon entity = couponRepository.findById(id).orElseThrow(() -> NotFoundException.of("Coupon", id));
        if (request.name() != null) {
            entity.setName(request.name().trim());
        }
        if (request.discountValue() != null) {
            entity.setDiscountValue(request.discountValue());
        }
        if (request.validFrom() != null) {
            entity.setValidFrom(request.validFrom());
        }
        if (request.validTo() != null) {
            entity.setValidTo(request.validTo());
        }
        Coupon saved = couponRepository.save(entity);
        audit.audit(actorId, "UPDATE", "Coupon", saved.getId(), "Updated coupon");
        return toResponse(saved);
    }

    @Transactional
    public void archiveCoupon(UUID id, UUID actorId) {
        Coupon entity = couponRepository.findById(id).orElseThrow(() -> NotFoundException.of("Coupon", id));
        entity.setStatus(Status.ARCHIVED);
        couponRepository.save(entity);
        audit.audit(actorId, "ARCHIVE", "Coupon", entity.getId(), "Archived coupon");
    }

    // ---------------------------------------------------------------- coupon validation

    /**
     * Validates a coupon against a plan and returns the server-calculated discount and final amount
     * ({@code §15.1, §33}). The client never determines the final price, and this response is not
     * trusted at purchase creation — the same rules are re-run there ({@code §33}).
     */
    @Transactional(readOnly = true)
    public CommerceDtos.ValidateCouponResponse validateCoupon(CommerceDtos.ValidateCouponRequest request) {
        SubscriptionPlan plan = requirePlan(request.subscriptionPlanId());
        Optional<Coupon> coupon = couponRepository.findByCode(normalizeCode(request.code()));
        if (coupon.isEmpty()) {
            metrics.couponValidated(false);
            return rejected(request, plan, "COUPON_NOT_FOUND");
        }
        Coupon entity = coupon.get();
        Instant now = Instant.now();
        if (entity.getStatus() != Status.ACTIVE) {
            metrics.couponValidated(false);
            return rejected(request, plan, "COUPON_INACTIVE");
        }
        if (entity.getValidFrom() != null && now.isBefore(entity.getValidFrom())) {
            metrics.couponValidated(false);
            return rejected(request, plan, "COUPON_NOT_YET_VALID");
        }
        if (entity.getValidTo() != null && now.isAfter(entity.getValidTo())) {
            metrics.couponValidated(false);
            return rejected(request, plan, "COUPON_EXPIRED");
        }
        PricingCalculator.PricingResult pricing = pricingCalculator.calculate(planSnapshot(plan),
                Optional.of(couponSnapshot(entity)));
        metrics.couponValidated(true);
        return new CommerceDtos.ValidateCouponResponse(true, entity.getCode(), plan.getId(), pricing.listPrice(),
                pricing.discount(), pricing.finalAmount(), pricing.currency(), null);
    }

    /**
     * Resolves the coupon for a purchase, rejecting a supplied code that is not currently valid
     * ({@code §31, §45.2}). A missing code means no coupon.
     */
    @Transactional(readOnly = true)
    public Optional<Coupon> resolveValidCoupon(String code, SubscriptionPlan plan) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        CommerceDtos.ValidateCouponResponse validation = validateCoupon(
                new CommerceDtos.ValidateCouponRequest(code, plan.getId()));
        if (!validation.valid()) {
            throw new ValidationException("Coupon is not valid",
                    List.of(ApiError.of("couponCode", validation.reason(), "The coupon code cannot be applied")));
        }
        return couponRepository.findByCode(normalizeCode(code));
    }

    // ---------------------------------------------------------------- helpers

    @Transactional(readOnly = true)
    public AccessPackage requirePackage(UUID id) {
        return packageRepository.findById(id).orElseThrow(() -> NotFoundException.of("Access package", id));
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan requirePlan(UUID id) {
        return planRepository.findById(id).orElseThrow(() -> NotFoundException.of("Subscription plan", id));
    }

    static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    static PricingCalculator.PlanSnapshot planSnapshot(SubscriptionPlan plan) {
        return new PricingCalculator.PlanSnapshot(plan.getId(), plan.getAccessPackageId(), plan.getCode(),
                plan.getPrice(), plan.getDurationDays() == null ? 0 : plan.getDurationDays());
    }

    static PricingCalculator.CouponSnapshot couponSnapshot(Coupon coupon) {
        return new PricingCalculator.CouponSnapshot(coupon.getId(), coupon.getCode(), coupon.getDiscountValue());
    }

    private CommerceDtos.ValidateCouponResponse rejected(CommerceDtos.ValidateCouponRequest request,
            SubscriptionPlan plan, String reason) {
        return new CommerceDtos.ValidateCouponResponse(false, normalizeCode(request.code()), plan.getId(), null, null,
                null, null, reason);
    }

    private CommerceDtos.AccessPackageResponse toResponse(AccessPackage entity) {
        return new CommerceDtos.AccessPackageResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getStatus() == null ? null : entity.getStatus().name());
    }

    private CommerceDtos.PackageCollectionResponse toResponse(AccessPackageCollection entity) {
        return new CommerceDtos.PackageCollectionResponse(entity.getId(), entity.getAccessPackageId(),
                entity.getAssessmentCollectionId(), entity.getStatus() == null ? null : entity.getStatus().name());
    }

    private CommerceDtos.SubscriptionPlanResponse toResponse(SubscriptionPlan entity) {
        return new CommerceDtos.SubscriptionPlanResponse(entity.getId(), entity.getAccessPackageId(),
                entity.getCode(), entity.getName(), entity.getDurationDays(), entity.getPrice(),
                entity.getStatus() == null ? null : entity.getStatus().name());
    }

    private CommerceDtos.CouponResponse toResponse(Coupon entity) {
        return new CommerceDtos.CouponResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDiscountValue(), entity.getValidFrom(), entity.getValidTo(),
                entity.getStatus() == null ? null : entity.getStatus().name());
    }
}
