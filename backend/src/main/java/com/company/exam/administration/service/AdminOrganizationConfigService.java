package com.company.exam.administration.service;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.repository.OrganizationSettingRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.identity.entity.Organization;
import com.company.exam.identity.entity.User;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.identity.repository.UserRepository;
import com.company.exam.platform.dto.PlatformDtos;
import com.company.exam.platform.service.PlatformProviderService;
import tools.jackson.core.type.TypeReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * ADMIN organization-scoped configuration ({@code specs/05 §73.1}; ADR-0037).
 *
 * <p>Composes only what an organization administrator may see: the organization's identity and
 * timezone, feature flags, masked provider status, the migration version, and the application
 * version. It never returns installation-level detail (git commit, dependencies, container
 * status) or provider secrets.
 */
@Service
public class AdminOrganizationConfigService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingRepository settingRepository;
    private final PlatformProviderService providerService;
    private final Flyway flyway;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final SecurityEventLogger audit;

    public AdminOrganizationConfigService(UserRepository userRepository,
            OrganizationRepository organizationRepository, OrganizationSettingRepository settingRepository,
            PlatformProviderService providerService, Flyway flyway, Environment environment,
            ObjectMapper objectMapper, SecurityEventLogger audit) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.settingRepository = settingRepository;
        this.providerService = providerService;
        this.flyway = flyway;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdministrationDtos.OrganizationConfigResponse get(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("user not found"));
        Organization organization = organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException("organization not found"));
        Map<String, Object> config = readConfig(organization.getId());
        PlatformDtos.ProvidersResponse providers = providerService.get();

        AdministrationDtos.OrganizationConfigOrganization orgView = toOrganizationView(organization, config);
        AdministrationDtos.OrganizationConfigProviders providerStatus =
                new AdministrationDtos.OrganizationConfigProviders(providers.email().configured(),
                        providers.payment().configured());
        return new AdministrationDtos.OrganizationConfigResponse(orgView, featureFlags(config), providerStatus,
                migrationVersion(), environment.getProperty("app.info.version"));
    }

    private AdministrationDtos.OrganizationConfigOrganization toOrganizationView(Organization organization,
            Map<String, Object> config) {
        return new AdministrationDtos.OrganizationConfigOrganization(organization.getName(),
                organization.getCode(), timezone(config), organization.getDescription(),
                organization.getAddressLine1(), organization.getAddressLine2(), organization.getCity(),
                organization.getStateRegion(), organization.getPostalCode(), organization.getCountry(),
                organization.getContactEmail(), organization.getContactPhone(), organization.getWebsiteUrl(),
                organization.getLogoUrl());
    }

    /**
     * Updates the caller's organization profile (V37, ADR-0051).
     *
     * <p>Scoped to the caller's own organization: the id is read from the user row, never from the
     * request, so an administrator cannot edit another tenant by guessing an id. Every mutation writes
     * an audit row in the same transaction, like every other administrative write.
     *
     * <p>Fields are replaced wholesale from the request; a null means "clear this field", which is how
     * an optional field is emptied. Blank strings are normalised to null so the UI can round-trip an
     * untouched form without storing empty strings.
     */
    @Transactional
    public AdministrationDtos.OrganizationConfigOrganization updateProfile(
            AdministrationDtos.UpdateOrganizationProfileRequest request, UUID actorId) {
        User user = userRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("user not found"));
        Organization organization = organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new IllegalStateException("organization not found"));

        if (request.name() != null && !request.name().isBlank()) {
            organization.setName(request.name().trim());
        }
        organization.setDescription(blankToNull(request.description()));
        organization.setAddressLine1(blankToNull(request.addressLine1()));
        organization.setAddressLine2(blankToNull(request.addressLine2()));
        organization.setCity(blankToNull(request.city()));
        organization.setStateRegion(blankToNull(request.stateRegion()));
        organization.setPostalCode(blankToNull(request.postalCode()));
        organization.setCountry(blankToNull(request.country()));
        organization.setContactEmail(blankToNull(request.contactEmail()));
        organization.setContactPhone(blankToNull(request.contactPhone()));
        organization.setWebsiteUrl(blankToNull(request.websiteUrl()));
        organization.setLogoUrl(blankToNull(request.logoUrl()));

        organizationRepository.save(organization);
        audit.audit(actorId, "UPDATE", "Organization", organization.getId(), "Updated the organization profile");

        return toOrganizationView(organization, readConfig(organization.getId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> readConfig(UUID organizationId) {
        return settingRepository.findByOrganizationId(organizationId)
                .map(setting -> read(setting.getConfiguration()))
                .orElse(Map.of());
    }

    private Map<String, Object> read(String configuration) {
        try {
            if (configuration == null || configuration.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(configuration, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (RuntimeException unreadable) {
            return new LinkedHashMap<>();
        }
    }

    private String timezone(Map<String, Object> config) {
        Object display = config.get("display");
        if (display instanceof Map<?, ?> map) {
            Object zone = map.get("timeZone");
            if (zone != null) {
                return String.valueOf(zone);
            }
        }
        return "UTC";
    }

    private Map<String, Object> featureFlags(Map<String, Object> config) {
        Object flags = config.get("featureFlags");
        if (flags instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return Map.of();
    }

    private Integer migrationVersion() {
        MigrationInfo current = flyway.info().current();
        if (current == null || current.getVersion() == null) {
            return null;
        }
        try {
            return Integer.valueOf(current.getVersion().getVersion());
        } catch (NumberFormatException nonNumeric) {
            return null;
        }
    }
}
