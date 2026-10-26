package com.company.exam.platform.service;

import com.company.exam.administration.entity.OrganizationSetting;
import com.company.exam.administration.repository.OrganizationSettingRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.repository.OrganizationRepository;
import com.company.exam.platform.dto.PlatformDtos;
import tools.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * SUPER_ADMIN provider configuration ({@code specs/05 §72.8, §72.9}; ADR-0033 C5).
 *
 * <p>Provider configuration is stored in {@code organization_setting.configuration} for the
 * PLATFORM system organization — no new table. Only reference adapters exist in V1.5
 * (GAP-099, GAP-108), so no credentials are persisted and responses never carry secrets. The
 * masked view reflects what is configured, never the underlying keys.
 */
@Service
public class PlatformProviderService {

    private static final String PROVIDERS_KEY = "providers";
    private static final String EMAIL_KEY = "email";
    private static final String PAYMENT_KEY = "payment";
    private static final String PROVIDER_NAME = "providerName";
    private static final String FROM_ADDRESS = "fromAddress";
    private static final String UPDATED_AT = "updatedAt";
    private static final String REFERENCE_PROVIDER = "reference";

    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingRepository settingRepository;
    private final ObjectMapper objectMapper;
    private final SecurityEventLogger securityEvents;

    public PlatformProviderService(OrganizationRepository organizationRepository,
            OrganizationSettingRepository settingRepository, ObjectMapper objectMapper,
            SecurityEventLogger securityEvents) {
        this.organizationRepository = organizationRepository;
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
        this.securityEvents = securityEvents;
    }

    @Transactional(readOnly = true)
    public PlatformDtos.ProvidersResponse get() {
        UUID platformOrgId = platformOrganizationId();
        Map<String, Object> providers = settingRepository.findByOrganizationId(platformOrgId)
                .map(setting -> providers(read(setting.getConfiguration())))
                .orElse(Map.of());
        return toResponse(providers);
    }

    @Transactional
    public PlatformDtos.ProvidersResponse update(PlatformDtos.ProviderConfigUpdate request, UUID actorId) {
        UUID platformOrgId = platformOrganizationId();
        OrganizationSetting setting = settingRepository.lockByOrganizationId(platformOrgId)
                .orElseGet(() -> newSetting(platformOrgId));
        Map<String, Object> config = read(setting.getConfiguration());

        Map<String, Object> providers = new LinkedHashMap<>();
        providers.put(EMAIL_KEY, emailBlock(request.email()));
        providers.put(PAYMENT_KEY, paymentBlock(request.payment()));
        config.put(PROVIDERS_KEY, providers);

        setting.setConfiguration(write(config));
        if (setting.getId() == null) {
            setting.setStatus(Status.ACTIVE);
        }
        settingRepository.save(setting);
        securityEvents.audit(actorId, "PROVIDER_CONFIG_UPDATE", "OrganizationSetting", setting.getId(),
                "Updated platform provider configuration");
        return toResponse(providers);
    }

    private Map<String, Object> emailBlock(PlatformDtos.ProviderFields fields) {
        Map<String, Object> block = new LinkedHashMap<>();
        if (fields != null) {
            block.put(PROVIDER_NAME, providerName(fields.providerName()));
            block.put(FROM_ADDRESS, blankToNull(fields.fromAddress()));
        }
        block.put(UPDATED_AT, Instant.now().toString());
        return block;
    }

    private Map<String, Object> paymentBlock(PlatformDtos.ProviderFields fields) {
        Map<String, Object> block = new LinkedHashMap<>();
        if (fields != null) {
            block.put(PROVIDER_NAME, providerName(fields.providerName()));
        }
        block.put(UPDATED_AT, Instant.now().toString());
        return block;
    }

    private PlatformDtos.ProvidersResponse toResponse(Map<String, Object> providers) {
        Map<String, Object> email = subMap(providers, EMAIL_KEY);
        Map<String, Object> payment = subMap(providers, PAYMENT_KEY);
        PlatformDtos.EmailProviderView emailView = new PlatformDtos.EmailProviderView(
                hasName(email), string(email, PROVIDER_NAME), string(email, FROM_ADDRESS),
                instant(email, UPDATED_AT));
        PlatformDtos.PaymentProviderView paymentView = new PlatformDtos.PaymentProviderView(
                hasName(payment), string(payment, PROVIDER_NAME), false, instant(payment, UPDATED_AT));
        return new PlatformDtos.ProvidersResponse(emailView, paymentView);
    }

    private Map<String, Object> providers(Map<String, Object> config) {
        return subMap(config, PROVIDERS_KEY);
    }

    private boolean hasName(Map<String, Object> block) {
        return string(block, PROVIDER_NAME) != null;
    }

    private String providerName(String value) {
        String name = blankToNull(value);
        return name == null ? REFERENCE_PROVIDER : name;
    }

    private OrganizationSetting newSetting(UUID organizationId) {
        OrganizationSetting setting = new OrganizationSetting();
        setting.setOrganizationId(organizationId);
        setting.setConfiguration("{}");
        return setting;
    }

    private UUID platformOrganizationId() {
        return organizationRepository.findByCode(PlatformOrganizationService.PLATFORM_CODE)
                .orElseThrow(() -> new IllegalStateException("PLATFORM system organization is not seeded"))
                .getId();
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

    private String write(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (RuntimeException unwritable) {
            throw new IllegalStateException("Provider configuration is not serializable", unwritable);
        }
    }

    private static Map<String, Object> subMap(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(Map<String, Object> map, String key) {
        String value = string(map, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException invalid) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
