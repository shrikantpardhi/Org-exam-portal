package com.company.exam.delivery.engine;

import com.company.exam.assessment.entity.EngineType;

/**
 * The approved runtime engine interface ({@code specs/14-P5-Delivery-V1.5.md §33}).
 *
 * <p>Engines are declarative: they resolve the normalized {@link DeliveryConfiguration}, navigation,
 * timer, submission, and validation policy from the published assessment context. The frontend never
 * branches on engine names ({@code §33, §59}).
 */
public interface ExamEngine {

    EngineType type();

    DeliveryConfiguration resolveConfiguration(ExamEngineContext context);
}
