package com.company.exam.administration.service;

import com.company.exam.administration.api.LookupCatalog;
import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.entity.Lookup;
import com.company.exam.administration.repository.LookupRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.cache.CacheService;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lookup master data ({@code specs/20-P11-Administration-V1.5.md §10}).
 *
 * <p>Deletion is conservative: a value that is an application contract is archived rather than
 * physically removed, because consumers reference category and code ({@code §10.4}).
 */
@Service
public class LookupService implements LookupCatalog {

    private static final String CACHE_KEY = "administration:lookup:";

    private final LookupRepository lookupRepository;
    private final SecurityEventLogger audit;
    private final CacheService cache;

    public LookupService(LookupRepository lookupRepository, SecurityEventLogger audit, CacheService cache) {
        this.lookupRepository = lookupRepository;
        this.audit = audit;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<AdministrationDtos.LookupResponse> list(String category, Status status) {
        return lookupRepository.findAll().stream()
                .filter(lookup -> category == null || category.isBlank() || lookup.getCategory().equals(category))
                .filter(lookup -> status == null || lookup.getStatus() == status)
                .sorted(java.util.Comparator
                        .comparing(Lookup::getCategory)
                        .thenComparing(lookup -> lookup.getDisplayOrder() == null ? Integer.MAX_VALUE
                                : lookup.getDisplayOrder())
                        .thenComparing(Lookup::getCode))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdministrationDtos.LookupResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public AdministrationDtos.LookupResponse create(AdministrationDtos.CreateLookupRequest request, UUID actorId) {
        Lookup lookup = new Lookup();
        lookup.setCategory(request.category().trim().toUpperCase(Locale.ROOT));
        lookup.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        lookup.setName(request.name().trim());
        lookup.setDisplayOrder(request.displayOrder());
        lookup.setStatus(Status.ACTIVE);
        try {
            Lookup saved = lookupRepository.saveAndFlush(lookup);
            audit.audit(actorId, "CREATE", "Lookup", saved.getId(),
                    "Created lookup " + saved.getCategory() + "/" + saved.getCode());
            evict(saved.getCategory());
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("A lookup with that category and code already exists");
        }
    }

    @Transactional
    public AdministrationDtos.LookupResponse update(UUID id, AdministrationDtos.UpdateLookupRequest request,
            UUID actorId) {
        Lookup lookup = require(id);
        if (request.name() != null) {
            lookup.setName(request.name().trim());
        }
        if (request.displayOrder() != null) {
            lookup.setDisplayOrder(request.displayOrder());
        }
        Lookup saved = lookupRepository.saveAndFlush(lookup);
        audit.audit(actorId, "UPDATE", "Lookup", saved.getId(),
                "Updated lookup " + saved.getCategory() + "/" + saved.getCode());
        evict(saved.getCategory());
        return toResponse(saved);
    }

    /**
     * Conservative delete ({@code §10.4}): an ACTIVE value that consumers may reference is archived,
     * not physically removed; an already-inactive value may be removed outright.
     */
    @Transactional
    public void delete(UUID id, UUID actorId) {
        Lookup lookup = require(id);
        if (lookup.getStatus() == Status.ACTIVE) {
            lookup.setStatus(Status.ARCHIVED);
            lookupRepository.saveAndFlush(lookup);
            audit.audit(actorId, "ARCHIVE", "Lookup", lookup.getId(),
                    "Archived lookup " + lookup.getCategory() + "/" + lookup.getCode()
                            + " instead of physical deletion");
        } else {
            lookupRepository.delete(lookup);
            audit.audit(actorId, "DELETE", "Lookup", lookup.getId(),
                    "Deleted inactive lookup " + lookup.getCategory() + "/" + lookup.getCode());
        }
        evict(lookup.getCategory());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LookupValue> values(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        String key = CACHE_KEY + normalized;
        Optional<CachedLookups> cached = cache.get(key, CachedLookups.class);
        if (cached.isPresent()) {
            return cached.get().values();
        }
        List<LookupValue> resolved = lookupRepository
                .findByCategoryAndStatusOrderByDisplayOrderAsc(normalized, Status.ACTIVE).stream()
                .map(this::toValue)
                .toList();
        cache.put(key, new CachedLookups(resolved), Duration.ofMinutes(10));
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LookupValue> find(String category, String code) {
        if (category == null || code == null) {
            return Optional.empty();
        }
        return lookupRepository
                .findByCategoryAndCode(category.trim().toUpperCase(Locale.ROOT), code.trim().toUpperCase(Locale.ROOT))
                .map(this::toValue);
    }

    /** A serializable cache entry; the entity is never cached. */
    public record CachedLookups(List<LookupValue> values) {
    }

    private void evict(String category) {
        if (category != null) {
            cache.evict(CACHE_KEY + category);
        }
    }

    private Lookup require(UUID id) {
        return lookupRepository.findById(id).orElseThrow(() -> NotFoundException.of("Lookup", id));
    }

    private LookupValue toValue(Lookup lookup) {
        return new LookupValue(lookup.getCategory(), lookup.getCode(), lookup.getName(), lookup.getDisplayOrder(),
                lookup.getStatus() == Status.ACTIVE);
    }

    private AdministrationDtos.LookupResponse toResponse(Lookup lookup) {
        return new AdministrationDtos.LookupResponse(lookup.getId(), lookup.getCategory(), lookup.getCode(),
                lookup.getName(), lookup.getDisplayOrder(),
                lookup.getStatus() == null ? null : lookup.getStatus().name());
    }
}
