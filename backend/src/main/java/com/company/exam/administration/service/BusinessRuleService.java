package com.company.exam.administration.service;

import com.company.exam.administration.api.BusinessRuleResolver;
import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.entity.BusinessRule;
import com.company.exam.administration.repository.BusinessRuleRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.cache.CacheService;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.ConflictException;
import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.common.exception.ValidationException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Business-rule storage and governance ({@code specs/20-P11-Administration-V1.5.md §9}).
 *
 * <p>Administration stores and governs rules; the owning module interprets them. There is no DELETE:
 * lifecycle state is used instead of destructive deletion ({@code §9.5, §9.6}). Configuration is
 * data only — executable constructs are rejected ({@code §9.3}).
 */
@Service
public class BusinessRuleService implements BusinessRuleResolver {

    private static final String CACHE_KEY = "administration:business-rule:";
    private static final int MAX_CONFIGURATION_BYTES = 65_536;

    /** Keys that would turn a data rule into a hidden scripting engine ({@code §9.3}). */
    private static final List<String> FORBIDDEN_KEY_FRAGMENTS = List.of(
            "script", "javascript", "sql", "expression", "eval", "class", "method", "exec", "cmd",
            "shell", "file", "path", "url", "http", "network");

    private final BusinessRuleRepository ruleRepository;
    private final SecurityEventLogger audit;
    private final CacheService cache;
    private final ObjectMapper objectMapper;

    public BusinessRuleService(BusinessRuleRepository ruleRepository, SecurityEventLogger audit, CacheService cache,
            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.audit = audit;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdministrationDtos.BusinessRuleResponse> list(Status status) {
        return ruleRepository.findAll().stream()
                .filter(rule -> status == null || rule.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdministrationDtos.BusinessRuleResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public AdministrationDtos.BusinessRuleResponse create(AdministrationDtos.CreateBusinessRuleRequest request,
            UUID actorId) {
        validate(request.configuration());
        BusinessRule rule = new BusinessRule();
        rule.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        rule.setConfiguration(write(request.configuration()));
        rule.setStatus(Status.ACTIVE);
        try {
            BusinessRule saved = ruleRepository.saveAndFlush(rule);
            audit.audit(actorId, "CREATE", "BusinessRule", saved.getId(), "Created business rule " + saved.getCode());
            evict(saved.getCode());
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ConflictException("A business rule with that code already exists");
        }
    }

    @Transactional
    public AdministrationDtos.BusinessRuleResponse update(UUID id, AdministrationDtos.UpdateBusinessRuleRequest request,
            UUID actorId) {
        validate(request.configuration());
        BusinessRule rule = require(id);
        if (rule.getStatus() == Status.ARCHIVED) {
            throw new InvalidStateException("An archived business rule cannot be modified");
        }
        rule.setConfiguration(write(request.configuration()));
        BusinessRule saved = ruleRepository.saveAndFlush(rule);
        audit.audit(actorId, "UPDATE", "BusinessRule", saved.getId(), "Updated business rule " + saved.getCode());
        evict(saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedRule> resolve(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return Optional.empty();
        }
        String code = ruleCode.trim().toUpperCase(Locale.ROOT);
        String key = CACHE_KEY + code;
        Optional<CachedRule> cached = cache.get(key, CachedRule.class);
        if (cached.isPresent()) {
            return Optional.of(new ResolvedRule(code, cached.get().values()));
        }
        Optional<ResolvedRule> resolved = ruleRepository.findByCode(code)
                .filter(rule -> rule.getStatus() == Status.ACTIVE)
                .map(rule -> new ResolvedRule(code, read(rule)));
        resolved.ifPresent(rule -> cache.put(key, new CachedRule(rule.values()), Duration.ofMinutes(10)));
        return resolved;
    }

    /** A serializable cache entry; the entity is never cached. */
    public record CachedRule(Map<String, Object> values) {
    }

    private void validate(Map<String, Object> configuration) {
        if (configuration == null) {
            throw new ValidationException("Configuration is required", List.of(
                    com.company.exam.common.api.ApiError.of("configuration", "RULE_CONFIGURATION_REQUIRED",
                            "A configuration object is required")));
        }
        String serialized = write(configuration);
        if (serialized.length() > MAX_CONFIGURATION_BYTES) {
            throw new ValidationException("Configuration is too large", List.of(
                    com.company.exam.common.api.ApiError.of("configuration", "RULE_CONFIGURATION_TOO_LARGE",
                            "Rule configuration exceeds the permitted size")));
        }
        for (String key : configuration.keySet()) {
            String lowered = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_KEY_FRAGMENTS.stream().anyMatch(lowered::contains)) {
                throw new ValidationException("Configuration contains an executable construct", List.of(
                        com.company.exam.common.api.ApiError.of(key, "RULE_CONSTRUCT_FORBIDDEN",
                                "Rules are data, not executable code")));
            }
        }
    }

    private void evict(String code) {
        if (code != null) {
            cache.evict(CACHE_KEY + code);
        }
    }

    private BusinessRule require(UUID id) {
        return ruleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Business rule", id));
    }

    private Map<String, Object> read(BusinessRule rule) {
        try {
            return objectMapper.readValue(rule.getConfiguration(),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (RuntimeException unreadable) {
            return Map.of();
        }
    }

    private String write(Map<String, Object> values) {
        return objectMapper.writeValueAsString(values);
    }

    private AdministrationDtos.BusinessRuleResponse toResponse(BusinessRule rule) {
        return new AdministrationDtos.BusinessRuleResponse(rule.getId(), rule.getCode(), read(rule),
                rule.getStatus() == null ? null : rule.getStatus().name());
    }
}
