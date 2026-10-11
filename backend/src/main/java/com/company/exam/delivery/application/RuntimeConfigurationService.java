package com.company.exam.delivery.application;

import com.company.exam.assessment.api.AssessmentDefinitionCatalog;
import com.company.exam.assessment.entity.EngineType;
import com.company.exam.delivery.engine.DeliveryConfiguration;
import com.company.exam.delivery.engine.ExamEngineContext;
import com.company.exam.delivery.engine.ExamEngineRegistry;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves the normalized {@link DeliveryConfiguration} for a runtime attempt
 * ({@code specs/14-P5-Delivery-V1.5.md §33, §34, §95}).
 *
 * <p>The engine and delivery policy are read from the published assessment version, so the runtime
 * configuration is deterministic and survives recovery.
 */
@Service
public class RuntimeConfigurationService {

    private final ExamEngineRegistry engineRegistry;
    private final DeliveryPolicyService deliveryPolicyService;

    public RuntimeConfigurationService(ExamEngineRegistry engineRegistry, DeliveryPolicyService deliveryPolicyService) {
        this.engineRegistry = engineRegistry;
        this.deliveryPolicyService = deliveryPolicyService;
    }

    public DeliveryConfiguration resolve(AssessmentDefinitionCatalog.ScheduleView schedule,
            AssessmentDefinitionCatalog.PublishedAssessmentVersion version, List<String> sectionOrder) {
        EngineType engineType = version.engineType() == null
                ? EngineType.CUSTOM
                : EngineType.valueOf(version.engineType());
        ExamEngineContext context = new ExamEngineContext(engineType, version.id(), schedule.mode().name(),
                version.durationMinutes(), sectionOrder, deliveryPolicyService.resolve(version.deliveryPolicyId()));
        return engineRegistry.resolve(engineType).resolveConfiguration(context);
    }
}
