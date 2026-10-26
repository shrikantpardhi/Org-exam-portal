package com.company.exam.communication.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Communication configuration ({@code specs/19-P10-Communication-V1.5.md §33, §35, §85, §192}).
 *
 * <p>Retry defaults follow the frozen values: a bounded maximum number of attempts, exponential
 * backoff starting at 60 seconds with jitter and a 30-minute cap ({@code §33, §35}).
 */
@ConfigurationProperties(prefix = "app.communication")
public class CommunicationProperties {

    /** The active {@code EmailGateway} provider id. Only a fake provider ships with the platform. */
    private String provider = "FAKE";

    /** Maximum delivery attempts including the first ({@code §33}). */
    private int maxAttempts = 5;

    /** Backoff base delay ({@code §33}: "exponential starting at 60 seconds"). */
    private Duration backoffBase = Duration.ofSeconds(60);

    /** Backoff multiplier applied per attempt. */
    private double backoffMultiplier = 5.0;

    /** Maximum delay between attempts ({@code §33}: "cap: 30 minutes"). */
    private Duration backoffCap = Duration.ofMinutes(30);

    /** Jitter applied to the delay, as a fraction (0.20 = plus or minus 20 percent) ({@code §33}). */
    private double backoffJitter = 0.20;

    /** Notification batch size for fan-out inserts ({@code §48, §49}). */
    private int fanoutBatchSize = 500;

    /** Bounded worker batch size. */
    private int workerBatchSize = 50;

    /**
     * SMTP settings for the {@code SMTP} provider adapter (ADR-0048).
     *
     * <p>Transport (host, port, auth, timeouts) deliberately stays in Spring's standard
     * {@code spring.mail.*} tree, because {@code JavaMailSender} is auto-configured from it and
     * duplicating those keys here would create two sources of truth. Only the envelope sender lives
     * here: {@code JavaMailSender} has no notion of a default sender, and it belongs with the other
     * communication settings rather than floating in the transport tree.
     */
    private final Smtp smtp = new Smtp();

    /**
     * Settings for the {@code HTTP_API} provider adapter (ADR-0049).
     *
     * <p>The point of this block is that adopting a provider is configuration, not code: the URL, the
     * auth scheme, the header that carries the key and the JSON body template are all settings.
     */
    private final HttpApi httpApi = new HttpApi();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
        this.backoffBase = backoffBase;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public Duration getBackoffCap() {
        return backoffCap;
    }

    public void setBackoffCap(Duration backoffCap) {
        this.backoffCap = backoffCap;
    }

    public double getBackoffJitter() {
        return backoffJitter;
    }

    public void setBackoffJitter(double backoffJitter) {
        this.backoffJitter = backoffJitter;
    }

    public int getFanoutBatchSize() {
        return fanoutBatchSize;
    }

    public void setFanoutBatchSize(int fanoutBatchSize) {
        this.fanoutBatchSize = fanoutBatchSize;
    }

    public int getWorkerBatchSize() {
        return workerBatchSize;
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }

    public Smtp getSmtp() {
        return smtp;
    }

    public HttpApi getHttpApi() {
        return httpApi;
    }

    /**
     * Generic JSON-over-HTTP provider settings (ADR-0049).
     *
     * <p>Auth values are deliberately <strong>not</strong> here: a secret must never sit in a file that
     * gets committed or in a response body. Only the <em>name</em> of the environment variable that
     * holds the key is configuration ({@code apiKeyEnv}); the value is read from the environment at
     * send time.
     */
    public static class HttpApi {

        /** Full endpoint URL, for example {@code https://api.provider.test/v3/mail/send}. */
        private String url;

        /** Correlation id used to select this adapter; defaults to {@code HTTP_API}. */
        private String providerId = "HTTP_API";

        /**
         * How the API key is presented. One of {@code BEARER} (default),
         * {@code X_API_KEY}, {@code API_KEY} or {@code NONE}.
         */
        private String authScheme = "BEARER";

        /** Name of the header that carries the key. Overrides the scheme's default header. */
        private String authHeader;

        /**
         * Name of the environment variable holding the key. Never the key itself.
         *
         * <p>Read through Spring's relaxed binding, so {@code EMAIL_API_KEY} here resolves the
         * {@code EMAIL_API_KEY} environment variable or property.
         */
        private String apiKeyEnv;

        /**
         * JSON body template. Placeholders {@code {to}}, {@code {from}}, {@code {subject}},
         * {@code {html}} and {@code {text}} are substituted with JSON-escaped values.
         */
        private String bodyTemplate;

        /**
         * JSON field indicating a successful send, for example {@code status}. When the provider
         * answers 2xx this field is still checked if configured, because some providers return 200
         * with an error envelope.
         */
        private String successField;

        /** Value {@code successField} must have. Defaults to {@code success}. */
        private String successValue = "success";

        /** JSON field carrying the provider's message id, used for the correlation reference. */
        private String referenceField;

        /** JSON field carrying an error message, used for diagnostics. */
        private String errorField = "message";

        /** Connect timeout in milliseconds. Bounded so a hung provider cannot pin a worker. */
        private int connectTimeoutMs = 5000;

        /** Request timeout in milliseconds. */
        private int requestTimeoutMs = 10000;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getAuthScheme() {
            return authScheme;
        }

        public void setAuthScheme(String authScheme) {
            this.authScheme = authScheme;
        }

        public String getAuthHeader() {
            return authHeader;
        }

        public void setAuthHeader(String authHeader) {
            this.authHeader = authHeader;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public String getBodyTemplate() {
            return bodyTemplate;
        }

        public void setBodyTemplate(String bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
        }

        public String getSuccessField() {
            return successField;
        }

        public void setSuccessField(String successField) {
            this.successField = successField;
        }

        public String getSuccessValue() {
            return successValue;
        }

        public void setSuccessValue(String successValue) {
            this.successValue = successValue;
        }

        public String getReferenceField() {
            return referenceField;
        }

        public void setReferenceField(String referenceField) {
            this.referenceField = referenceField;
        }

        public String getErrorField() {
            return errorField;
        }

        public void setErrorField(String errorField) {
            this.errorField = errorField;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }

    /** SMTP transport settings for the {@code SMTP} provider (ADR-0048). */
    public static class Smtp {

        /**
         * Envelope sender. Mailpit accepts any value; a real relay rejects an envelope it cannot
         * authenticate, so this is configuration rather than a constant.
         */
        private String from = "no-reply@exam-platform.local";

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }
}
