package com.company.exam.communication.gateway;

import com.company.exam.communication.config.CommunicationProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves the active {@link EmailGateway} by provider id ({@code specs/19-P10 §29}). */
@Component
public class EmailGatewayRegistry {

    private final List<EmailGateway> gateways;
    private final CommunicationProperties properties;

    public EmailGatewayRegistry(List<EmailGateway> gateways, CommunicationProperties properties) {
        this.gateways = List.copyOf(gateways);
        this.properties = properties;
    }

    public EmailGateway active() {
        return gateways.stream()
                .filter(gateway -> gateway.provider().value().equalsIgnoreCase(properties.getProvider()))
                .findFirst()
                .orElseGet(() -> gateways.stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No EmailGateway is configured")));
    }
}
