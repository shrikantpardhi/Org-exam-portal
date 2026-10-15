package com.company.exam.commerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Commerce configuration ({@code specs/18-P9-Commerce-V1.5.md §15.1, §32, §142}).
 *
 * <p>Only one currency and one rounding policy are supported. Rollout flags are logical operational
 * configuration and use no Commerce tables ({@code §142}).
 */
@ConfigurationProperties(prefix = "app.commerce")
public class CommerceProperties {

    /** The single platform currency; there is no currency column in the frozen schema. */
    private String currency = "INR";

    /** A traceable pricing calculation version ({@code §15.2, §32}). */
    private String calculationVersion = "p9-1";

    /** The active {@code PaymentGateway} provider id. Only a test gateway ships with the platform. */
    private String gateway = "TEST";

    /** Callback signature secret for the reference gateway. Never persisted in domain tables. */
    private String gatewaySecret = "commerce-test-gateway-secret";

    /** Length of a paid grant is derived from the plan; manual grants use operator dates. */
    private boolean checkoutEnabled = false;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public void setCalculationVersion(String calculationVersion) {
        this.calculationVersion = calculationVersion;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getGatewaySecret() {
        return gatewaySecret;
    }

    public void setGatewaySecret(String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }

    public boolean isCheckoutEnabled() {
        return checkoutEnabled;
    }

    public void setCheckoutEnabled(boolean checkoutEnabled) {
        this.checkoutEnabled = checkoutEnabled;
    }
}
