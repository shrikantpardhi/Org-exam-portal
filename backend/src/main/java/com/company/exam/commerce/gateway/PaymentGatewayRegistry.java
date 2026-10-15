package com.company.exam.commerce.gateway;

import com.company.exam.commerce.config.CommerceProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves the active {@link PaymentGateway} by provider id ({@code specs/18-P9-Commerce-V1.5.md §13}).
 *
 * <p>The rest of Commerce depends on this registry, never on a concrete gateway.
 */
@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;
    private final CommerceProperties properties;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways, CommerceProperties properties) {
        this.gateways = List.copyOf(gateways);
        this.properties = properties;
    }

    /** The configured gateway, or the first registered when the configured id is absent. */
    public PaymentGateway active() {
        return gateways.stream()
                .filter(gateway -> gateway.provider().value().equalsIgnoreCase(properties.getGateway()))
                .findFirst()
                .orElseGet(() -> gateways.stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No PaymentGateway is configured")));
    }
}
