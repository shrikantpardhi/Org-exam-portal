package com.company.exam.communication.service;

import com.company.exam.common.cache.CacheService;
import com.company.exam.common.enums.Status;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.entity.EmailTemplate;
import com.company.exam.communication.repository.EmailTemplateRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email template administration and resolution
 * ({@code specs/19-P10-Communication-V1.5.md §21-§23, §57-§62}).
 *
 * <p>ACTIVE templates are cached in Redis with invalidation on write; PostgreSQL remains the source
 * of truth and a Redis outage degrades to a database read ({@code §61, §62}).
 */
@Service
public class EmailTemplateService {

    private static final String CACHE_PREFIX = "communication:template:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final EmailTemplateRepository templateRepository;
    private final CacheService cache;

    public EmailTemplateService(EmailTemplateRepository templateRepository, CacheService cache) {
        this.templateRepository = templateRepository;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<CommunicationDtos.EmailTemplateResponse> list(Status status, String search) {
        return templateRepository.findAll().stream()
                .filter(template -> status == null || template.getStatus() == status)
                .filter(template -> search == null || search.isBlank()
                        || template.getCode().toLowerCase(java.util.Locale.ROOT)
                                .contains(search.toLowerCase(java.util.Locale.ROOT)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunicationDtos.EmailTemplateResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public CommunicationDtos.EmailTemplateResponse create(CommunicationDtos.CreateEmailTemplateRequest request) {
        EmailTemplate template = new EmailTemplate();
        template.setCode(request.code().trim().toUpperCase(java.util.Locale.ROOT));
        template.setName(request.name().trim());
        template.setSubject(request.subject());
        template.setBody(request.body());
        try {
            EmailTemplate saved = templateRepository.saveAndFlush(template);
            evict(saved.getCode());
            return toResponse(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new com.company.exam.common.exception.ConflictException(
                    "An email template with that code already exists");
        }
    }

    @Transactional
    public CommunicationDtos.EmailTemplateResponse update(UUID id, CommunicationDtos.UpdateEmailTemplateRequest request) {
        EmailTemplate template = require(id);
        if (request.name() != null) {
            template.setName(request.name().trim());
        }
        if (request.subject() != null) {
            template.setSubject(request.subject());
        }
        if (request.body() != null) {
            template.setBody(request.body());
        }
        EmailTemplate saved = templateRepository.saveAndFlush(template);
        evict(saved.getCode());
        return toResponse(saved);
    }

    /** Archives (soft-deactivates) a template; a job requiring it then fails safely ({@code §60}). */
    @Transactional
    public void archive(UUID id) {
        EmailTemplate template = require(id);
        template.setStatus(Status.ARCHIVED);
        templateRepository.saveAndFlush(template);
        evict(template.getCode());
    }

    /**
     * Resolves an ACTIVE template by code, cache first. A missing or non-ACTIVE template is empty —
     * a random template is never substituted and the caller fails safely ({@code §59}).
     */
    @Transactional(readOnly = true)
    public Optional<EmailTemplate> resolveActive(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String key = CACHE_PREFIX + code;
        Optional<CachedTemplate> cached = cache.get(key, CachedTemplate.class);
        if (cached.isPresent()) {
            return Optional.of(cached.get().toEntity());
        }
        Optional<EmailTemplate> template = templateRepository.findByCode(code).filter(value -> value.getStatus() == Status.ACTIVE);
        template.ifPresent(value -> cache.put(key, CachedTemplate.from(value), CACHE_TTL));
        return template;
    }

    private void evict(String code) {
        if (code != null) {
            cache.evict(CACHE_PREFIX + code);
        }
    }

    private EmailTemplate require(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> NotFoundException.of("Email template", id));
    }

    private CommunicationDtos.EmailTemplateResponse toResponse(EmailTemplate template) {
        return new CommunicationDtos.EmailTemplateResponse(template.getId(), template.getCode(), template.getName(),
                template.getSubject(), template.getBody(),
                template.getStatus() == null ? null : template.getStatus().name());
    }

    /** A serializable cache entry; the entity itself is never cached. */
    public record CachedTemplate(UUID id, String code, String name, String subject, String body) {

        static CachedTemplate from(EmailTemplate template) {
            return new CachedTemplate(template.getId(), template.getCode(), template.getName(), template.getSubject(),
                    template.getBody());
        }

        EmailTemplate toEntity() {
            EmailTemplate template = new EmailTemplate();
            template.setId(id);
            template.setCode(code);
            template.setName(name);
            template.setSubject(subject);
            template.setBody(body);
            template.setStatus(Status.ACTIVE);
            return template;
        }
    }
}
