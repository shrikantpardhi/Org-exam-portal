package com.company.exam.administration.service;

import com.company.exam.administration.api.ConfigurationCatalog;
import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.entity.OrganizationSetting;
import com.company.exam.administration.repository.OrganizationSettingRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.cache.CacheService;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Authoritative organization configuration ({@code specs/20-P11-Administration-V1.5.md §8}).
 *
 * <p>Update flow: authenticate, permission check, load, validate shape, validate cross-field rules,
 * persist, audit, invalidate cache keys ({@code §8.4}). Concurrent updates to the same organization
 * are serialized server-side with a pessimistic row lock, since the frozen schema has no version
 * column ({@code §8.7}).
 */
@Service
public class OrganizationSettingService implements ConfigurationCatalog {

    private static final String CACHE_KEY = "administration:organization-setting:";

    private final OrganizationSettingRepository settingRepository;
    private final OrganizationSettingRegistry registry;
    private final SecurityEventLogger audit;
    private final CacheService cache;
    private final ObjectMapper objectMapper;

    public OrganizationSettingService(OrganizationSettingRepository settingRepository,
            OrganizationSettingRegistry registry, SecurityEventLogger audit, CacheService cache,
            ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.registry = registry;
        this.audit = audit;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdministrationDtos.OrganizationSettingResponse get(UUID organizationId) {
        Map<String, Object> values = settingRepository.findByOrganizationId(organizationId)
                .map(this::read)
                .orElseGet(() -> registry.withDefaults(Map.of()));
        return new AdministrationDtos.OrganizationSettingResponse(
                settingRepository.findByOrganizationId(organizationId).map(OrganizationSetting::getId).orElse(null),
                organizationId, values, registry.descriptors().stream()
                        .map(key -> new AdministrationDtos.SettingKeyDescriptor(key.key(),
                                key.type().getSimpleName(), key.defaultValue(), key.mutable(),
                                key.securityClassification()))
                        .toList());
    }

    /** Validates and persists the configuration for the deployment organization ({@code §8.4}). */
    @Transactional
    public AdministrationDtos.OrganizationSettingResponse update(UUID organizationId,
            Map<String, Object> supplied, UUID actorId) {
        registry.validate(supplied);
        OrganizationSetting setting = settingRepository.lockByOrganizationId(organizationId)
                .orElseGet(() -> {
                    OrganizationSetting created = new OrganizationSetting();
                    created.setOrganizationId(organizationId);
                    return created;
                });
        setting.setConfiguration(write(supplied));
        setting.setStatus(Status.ACTIVE);
        OrganizationSetting saved = settingRepository.saveAndFlush(setting);
        audit.audit(actorId, "UPDATE", "OrganizationSetting", saved.getId(),
                "Updated organization configuration for organization " + organizationId);
        cache.evict(CACHE_KEY + organizationId);
        return new AdministrationDtos.OrganizationSettingResponse(saved.getId(), organizationId,
                registry.withDefaults(supplied), registry.descriptors().stream()
                        .map(key -> new AdministrationDtos.SettingKeyDescriptor(key.key(),
                                key.type().getSimpleName(), key.defaultValue(), key.mutable(),
                                key.securityClassification()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationConfiguration> organizationConfiguration(UUID organizationId) {
        if (organizationId == null) {
            return Optional.empty();
        }
        String key = CACHE_KEY + organizationId;
        Optional<CachedSetting> cached = cache.get(key, CachedSetting.class);
        if (cached.isPresent()) {
            return Optional.of(new OrganizationConfiguration(organizationId, cached.get().values()));
        }
        Optional<OrganizationConfiguration> resolved = settingRepository.findByOrganizationId(organizationId)
                .filter(setting -> setting.getStatus() == Status.ACTIVE)
                .map(setting -> new OrganizationConfiguration(organizationId, read(setting)));
        resolved.ifPresent(configuration -> cache.put(key, new CachedSetting(configuration.values()),
                java.time.Duration.ofMinutes(10)));
        return resolved;
    }

    /** A serializable cache entry; the entity is never cached. */
    public record CachedSetting(Map<String, Object> values) {
    }

    private Map<String, Object> read(OrganizationSetting setting) {
        try {
            Map<String, Object> values = objectMapper.readValue(setting.getConfiguration(),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            return registry.withDefaults(values);
        } catch (RuntimeException unreadable) {
            throw NotFoundException.of("Organization setting", setting.getOrganizationId());
        }
    }

    private String write(Map<String, Object> values) {
        return objectMapper.writeValueAsString(values == null ? Map.of() : values);
    }
}
