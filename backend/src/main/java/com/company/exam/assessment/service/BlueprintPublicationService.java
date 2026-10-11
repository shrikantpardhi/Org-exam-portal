package com.company.exam.assessment.service;

import com.company.exam.assessment.blueprint.BlueprintConfiguration;
import com.company.exam.assessment.blueprint.BlueprintConfigurationParser;
import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.entity.BlueprintVersion;
import com.company.exam.assessment.mapper.AssessmentMapper;
import com.company.exam.assessment.repository.BlueprintVersionRepository;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Blueprint publication ({@code §22, §52, §181}). */
@Service
public class BlueprintPublicationService {

    private final BlueprintVersionService versionService;
    private final BlueprintVersionRepository versionRepository;
    private final BlueprintConfigurationParser configurationParser;
    private final AssessmentMapper mapper;
    private final SecurityEventLogger audit;

    public BlueprintPublicationService(BlueprintVersionService versionService,
            BlueprintVersionRepository versionRepository, BlueprintConfigurationParser configurationParser,
            AssessmentMapper mapper, SecurityEventLogger audit) {
        this.versionService = versionService;
        this.versionRepository = versionRepository;
        this.configurationParser = configurationParser;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional
    public AssessmentDtos.BlueprintVersionResponse publish(UUID blueprintId, UUID versionId, UUID actorId) {
        BlueprintVersion version = versionService.requireVersion(blueprintId, versionId);
        if (version.isPublished()) {
            throw new ConflictException("This blueprint version is already published");
        }
        BlueprintConfiguration configuration = configurationParser.parse(version.getConfiguration());
        configurationParser.validate(configuration);

        version.setPublished(true);
        version.setPublishedBy(actorId);
        version.setPublishedOn(Instant.now());
        BlueprintVersion saved = versionRepository.save(version);
        audit.audit(actorId, "PUBLISH", "BlueprintVersion", saved.getId(), "Published blueprint version");
        return mapper.toResponse(saved);
    }
}
