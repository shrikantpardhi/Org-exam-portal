package com.company.exam.identity.service;

import com.company.exam.common.enums.Status;
import com.company.exam.identity.api.AnalyticsIdentityCatalog;
import com.company.exam.identity.api.CandidateDirectory;
import com.company.exam.identity.repository.OrganizationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link AnalyticsIdentityCatalog} ({@code specs/17-P8-Analytics-V1.5.md §41, §73}). */
@Service
public class AnalyticsIdentityCatalogService implements AnalyticsIdentityCatalog {

    private final CandidateDirectory candidateDirectory;
    private final OrganizationRepository organizationRepository;

    public AnalyticsIdentityCatalogService(CandidateDirectory candidateDirectory,
            OrganizationRepository organizationRepository) {
        this.candidateDirectory = candidateDirectory;
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeStudentUserIds() {
        return candidateDirectory.activeCandidateUserIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> organizationIds() {
        return organizationRepository.findByStatus(Status.ACTIVE).stream()
                .map(com.company.exam.identity.entity.Organization::getId)
                .toList();
    }
}
