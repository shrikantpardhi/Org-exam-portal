package com.company.exam.delivery.engine;

import com.company.exam.assessment.entity.EngineType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registry-based engine resolution ({@code specs/14-P5-Delivery-V1.5.md §33}).
 *
 * <p>Every engine registers itself by type. When no engine is registered for a published type, the
 * declarative pilot engine handles it, so a new engine can be added without changing Delivery.
 */
@Component
public class ExamEngineRegistry {

    private final Map<EngineType, ExamEngine> engines = new EnumMap<>(EngineType.class);
    private final ExamEngine fallback;

    public ExamEngineRegistry(List<ExamEngine> registered) {
        for (ExamEngine engine : registered) {
            engines.put(engine.type(), engine);
        }
        this.fallback = engines.getOrDefault(EngineType.CUSTOM,
                registered.isEmpty() ? null : registered.get(0));
    }

    public ExamEngine resolve(EngineType type) {
        if (type != null && engines.containsKey(type)) {
            return engines.get(type);
        }
        if (fallback == null) {
            throw new IllegalStateException("No ExamEngine is registered");
        }
        return fallback;
    }
}
