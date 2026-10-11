package com.company.exam.delivery.engine;

import com.company.exam.assessment.entity.EngineType;
import org.springframework.stereotype.Component;

/**
 * The pilot's declarative engine ({@code specs/14-P5-Delivery-V1.5.md §32-§34, §5}).
 *
 * <p>For the pilot, engine-specific behavior is expressed as normalized declarative configuration
 * derived from the published duration, mode, and delivery policy. A dedicated engine per
 * examination body is a later, non-breaking addition behind the same {@link ExamEngine} interface.
 */
@Component
public class DeclarativeExamEngine implements ExamEngine {

    private static final String CONTENT_FORMAT = "MARKDOWN_LATEX_V1";

    @Override
    public EngineType type() {
        return EngineType.CUSTOM;
    }

    @Override
    public DeliveryConfiguration resolveConfiguration(ExamEngineContext context) {
        DeliveryPolicyAttributes policy = context.deliveryPolicyAttributes() == null
                ? DeliveryPolicyAttributes.defaults()
                : context.deliveryPolicyAttributes();
        boolean practice = "PRACTICE".equals(context.mode());
        Integer durationSeconds = context.durationMinutes() == null ? null : context.durationMinutes() * 60;
        boolean enforced = !practice && durationSeconds != null;
        String navigationMode = practice || policy.allowBackNavigation() ? "FREE" : "SEQUENTIAL";

        return new DeliveryConfiguration(
                new DeliveryConfiguration.Capabilities(practice, policy.allowClearResponse(),
                        policy.allowMarkForReview(), practice, practice),
                new DeliveryConfiguration.NavigationPolicy(navigationMode, policy.allowBackNavigation(),
                        policy.allowSectionSwitch(), policy.allowQuestionReview()),
                new DeliveryConfiguration.TimerPolicy("GLOBAL", durationSeconds, enforced),
                new DeliveryConfiguration.SubmissionPolicy(policy.autoSubmitOnExpiry(), true),
                new DeliveryConfiguration.ValidationPolicy(true, 10000, policy.autoSaveIntervalSeconds()),
                new DeliveryConfiguration.PresentationPolicy(true, true, true, CONTENT_FORMAT,
                        context.sectionOrder() == null ? java.util.List.of() : context.sectionOrder()));
    }
}
