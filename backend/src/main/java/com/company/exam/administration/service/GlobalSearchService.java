package com.company.exam.administration.service;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.administration.repository.AuditLogRepository;
import com.company.exam.common.exception.ValidationException;
import com.company.exam.identity.api.AdminUserDirectory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

/**
 * Global search ({@code specs/20-P11-Administration-V1.5.md §37-§40}).
 *
 * <p>Explicit handlers, never a dynamic table browser: query parameters are not mapped into table
 * names, resource names are resolved from the frozen allow-list, and every resource is gated by its
 * own permission ({@code §39, §40, §86}). Results for which the caller lacks the resource permission
 * are omitted entirely.
 */
@Service
public class GlobalSearchService {

    /** One searchable resource ({@code §39}). */
    public interface SearchHandler {

        /** The frozen resource name, for example {@code USER}. */
        String resource();

        /** The permission required to search this resource. */
        String permission();

        List<AdministrationDtos.SearchResult> search(String query, int limit);
    }

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_LIMIT = 50;

    private final Map<String, SearchHandler> handlers;

    public GlobalSearchService(List<SearchHandler> handlers, AdminUserDirectory userDirectory,
            AuditLogRepository auditLogRepository) {
        Map<String, SearchHandler> registered = new LinkedHashMap<>();
        registered.put("USER", new UserSearchHandler(userDirectory));
        registered.put("AUDIT_LOG", new AuditLogSearchHandler(auditLogRepository));
        handlers.forEach(handler -> registered.put(handler.resource(), handler));
        this.handlers = Map.copyOf(registered);
    }

    /** The frozen allow-list advertised to clients, without exposing handler internals ({@code §39}). */
    public List<AdministrationDtos.SupportedResource> supportedResources() {
        return handlers.values().stream()
                .map(handler -> new AdministrationDtos.SupportedResource(handler.resource(), List.of("SEARCH"),
                        handler.permission()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdministrationDtos.SearchResponse search(String query, String resource,
            Integer limit, Authentication authentication,
            java.util.function.BiPredicate<Authentication, String> hasPermission) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("A search query is required");
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ValidationException("The search query exceeds the maximum length of " + MAX_QUERY_LENGTH);
        }
        int bounded = limit == null ? 10 : Math.max(1, Math.min(limit, MAX_LIMIT));

        List<SearchHandler> selected = resource == null || resource.isBlank()
                ? List.copyOf(handlers.values())
                : handlers.values().stream().filter(handler -> handler.resource().equals(resource)).toList();
        if (selected.isEmpty()) {
            throw new ValidationException("Unsupported search resource");
        }

        List<AdministrationDtos.SearchResult> results = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        for (SearchHandler handler : selected) {
            if (!hasPermission.test(authentication, handler.permission())) {
                continue;
            }
            searched.add(handler.resource());
            results.addAll(handler.search(trimmed, bounded));
        }
        boolean truncated = results.size() > bounded;
        List<AdministrationDtos.SearchResult> boundedResults = truncated
                ? results.subList(0, bounded)
                : results;
        return new AdministrationDtos.SearchResponse(trimmed, searched, List.copyOf(boundedResults), truncated);
    }

    /** USER: first name, last name (contains), email and mobile (exact) ({@code §39}). */
    static final class UserSearchHandler implements SearchHandler {

        private final AdminUserDirectory userDirectory;

        UserSearchHandler(AdminUserDirectory userDirectory) {
            this.userDirectory = userDirectory;
        }

        @Override
        public String resource() {
            return "USER";
        }

        @Override
        public String permission() {
            return "GLOBAL_SEARCH_USER";
        }

        @Override
        public List<AdministrationDtos.SearchResult> search(String query, int limit) {
            return userDirectory.search(query, limit).stream()
                    .map(user -> new AdministrationDtos.SearchResult("USER", user.userId(),
                            displayName(user), Map.of("status", user.status() == null ? "" : user.status())))
                    .toList();
        }

        private String displayName(AdminUserDirectory.AdminUserView user) {
            String first = user.firstName() == null ? "" : user.firstName();
            String last = user.lastName() == null ? "" : user.lastName();
            String name = (first + " " + last).trim();
            return name.isEmpty() ? "user" : name;
        }
    }

    /** AUDIT_LOG: entity name and entity id ({@code §39}). */
    static final class AuditLogSearchHandler implements SearchHandler {

        private final AuditLogRepository auditLogRepository;

        AuditLogSearchHandler(AuditLogRepository auditLogRepository) {
            this.auditLogRepository = auditLogRepository;
        }

        @Override
        public String resource() {
            return "AUDIT_LOG";
        }

        @Override
        public String permission() {
            return "GLOBAL_SEARCH_AUDIT";
        }

        @Override
        public List<AdministrationDtos.SearchResult> search(String query, int limit) {
            UUID parsedEntityId = null;
            try {
                parsedEntityId = UUID.fromString(query);
            } catch (IllegalArgumentException notAnId) {
                // Not an entity id: match on the entity name only.
            }
            final UUID entityId = parsedEntityId;
            return auditLogRepository
                    .findAll((root, criteriaQuery, builder) -> entityId == null
                            ? builder.equal(root.get("entityName"), query)
                            : builder.or(builder.equal(root.get("entityName"), query),
                                    builder.equal(root.get("entityId"), entityId)),
                            org.springframework.data.domain.PageRequest.of(0, limit))
                    .map(entry -> new AdministrationDtos.SearchResult("AUDIT_LOG", entry.getId(),
                            entry.getEntityName() + " " + entry.getAction(),
                            Map.of("entityName", entry.getEntityName() == null ? "" : entry.getEntityName(),
                                    "action", entry.getAction() == null ? "" : entry.getAction())))
                    .toList();
        }
    }
}
