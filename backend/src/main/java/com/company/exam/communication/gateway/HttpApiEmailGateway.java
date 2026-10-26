package com.company.exam.communication.gateway;

import com.company.exam.communication.config.CommunicationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A generic JSON-over-HTTP {@link EmailGateway} (ADR-0049).
 *
 * <p><b>Why this exists.</b> {@code specs/19-P10} §29 does not freeze an email provider, so naming one
 * would be a choice the product has not made. Almost every transactional provider (SendGrid, Mailgun,
 * Postmark, Brevo, Resend, SES's HTTP API) is the same shape: a POST of JSON to an endpoint with a key
 * in a header. This adapter implements that shape, so adopting a provider is configuration — URL,
 * auth scheme, header name and body template — rather than a new Java class per vendor.
 *
 * <p>It ships disabled. {@code app.communication.provider} must be set to this adapter's id
 * (default {@code HTTP_API}), and {@code url} must be present; a half-configured adapter refuses to
 * start rather than silently dropping mail.
 *
 * <p><b>Secrets.</b> The API key is never a configuration value. Configuration carries the <em>name</em>
 * of the environment variable ({@code apiKeyEnv}), and the value is read from the environment at send
 * time. That keeps keys out of {@code application.yml}, out of {@code docker-compose.yml}, and out of
 * anything a log or an error message could echo.
 *
 * <p><b>Failure classification follows the frozen retry contract (§34).</b> 2xx is success. 4xx is
 * permanent except 408 and 429, which are transient — a rejected payload will be rejected identically
 * forever, while a rate limit clears. 5xx and transport errors are transient.
 */
@Component
@ConditionalOnProperty(name = "app.communication.provider", havingValue = "HTTP_API")
public class HttpApiEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpApiEmailGateway.class);

    private final CommunicationProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final HttpClient httpClient;
    private final EmailProviderId providerId;

    @Autowired
    public HttpApiEmailGateway(CommunicationProperties properties, ObjectMapper objectMapper,
            Environment environment) {
        this(properties, objectMapper, environment, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getHttpApi().getConnectTimeoutMs()))
                .build());
    }

    /** Test seam: lets a unit test inject a client bound to a local stub server. */
    HttpApiEmailGateway(CommunicationProperties properties, ObjectMapper objectMapper,
            Environment environment, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = httpClient;
        this.providerId = new EmailProviderId(properties.getHttpApi().getProviderId());
        requireConfigured(properties.getHttpApi());
    }

    /**
     * Fails fast on a half-configured adapter.
     *
     * <p>Starting with no URL would mean every send throws at runtime and the retry budget is spent
     * discovering a configuration mistake. Refusing to start surfaces it once, at boot.
     */
    private static void requireConfigured(CommunicationProperties.HttpApi config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalStateException(
                    "app.communication.http-api.url is required when the HTTP_API email provider is selected");
        }
        if (config.getBodyTemplate() == null || config.getBodyTemplate().isBlank()) {
            throw new IllegalStateException(
                    "app.communication.http-api.body-template is required when the HTTP_API email provider is selected");
        }
    }

    @Override
    public EmailProviderId provider() {
        return providerId;
    }

    @Override
    public EmailSendResult send(RenderedEmail email) {
        if (email == null || email.toAddress() == null || email.toAddress().isBlank()) {
            log.warn("HTTP_API send rejected: no recipient address");
            return new EmailSendResult(null, SendOutcome.FAILED, false);
        }
        CommunicationProperties.HttpApi config = properties.getHttpApi();
        if (apiKey(config) == null) {
            // Permanent: the key will not appear by retrying, and the operator has to act.
            log.error("HTTP_API send rejected: environment variable {} is not set", config.getApiKeyEnv());
            return new EmailSendResult(null, SendOutcome.FAILED, false);
        }

        try {
            HttpRequest request = buildRequest(config, email);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return interpret(config, response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new EmailSendResult(null, SendOutcome.TIMEOUT, true);
        } catch (java.io.IOException transportFailure) {
            log.warn("HTTP_API send failed, treating as retryable: {}", transportFailure.getMessage());
            return new EmailSendResult(null, SendOutcome.TIMEOUT, true);
        } catch (RuntimeException unexpected) {
            // A template or serialization fault is a deployment error, not a provider one. Retrying
            // would repeat it exactly, so it is classified permanent.
            log.error("HTTP_API send failed while building the request: {}", unexpected.getMessage());
            return new EmailSendResult(null, SendOutcome.FAILED, false);
        }
    }

    private HttpRequest buildRequest(CommunicationProperties.HttpApi config, RenderedEmail email) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getUrl()))
                .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(renderBody(config, email)));

        String key = apiKey(config);
        String scheme = config.getAuthScheme() == null ? "BEARER" : config.getAuthScheme().toUpperCase();
        String header = config.getAuthHeader();
        switch (scheme) {
            case "NONE" -> { }
            case "X_API_KEY" -> builder.header(orDefault(header, "X-Api-Key"), key);
            case "API_KEY" -> builder.header(orDefault(header, "Api-Key"), key);
            default -> builder.header(orDefault(header, "Authorization"), "Bearer " + key);
        }
        return builder.build();
    }

    /** Substitutes the documented placeholders with JSON-escaped values. */
    private String renderBody(CommunicationProperties.HttpApi config, RenderedEmail email) {
        String template = config.getBodyTemplate();
        return template
                .replace("{to}", jsonString(email.toAddress()))
                .replace("{from}", jsonString(properties.getSmtp().getFrom()))
                .replace("{subject}", jsonString(orEmpty(email.subject())))
                .replace("{html}", jsonString(orEmpty(email.htmlBody())))
                .replace("{text}", jsonString(orEmpty(stripTags(email.htmlBody()))));
    }

    private EmailSendResult interpret(CommunicationProperties.HttpApi config, int status, String body) {
        if (status >= 200 && status < 300) {
            // Some providers answer 2xx with an error envelope, so a configured success field is
            // still checked before the send is called successful.
            String failure = declaredFailure(config, body);
            if (failure != null) {
                log.warn("HTTP_API send refused by provider: {}", failure);
                return new EmailSendResult(null, SendOutcome.FAILED, false);
            }
            return new EmailSendResult(reference(config, body), SendOutcome.SENT, false);
        }
        if (status == 408 || status == 429 || status >= 500) {
            log.warn("HTTP_API send got {}, treating as retryable", status);
            return new EmailSendResult(null, SendOutcome.FAILED, true);
        }
        // Remaining 4xx: bad payload, bad key, unknown recipient. None of these improve on retry.
        log.warn("HTTP_API send got {} (permanent): {}", status, truncate(body));
        return new EmailSendResult(null, SendOutcome.FAILED, false);
    }

    /** Returns the provider's declared failure message, or null when the payload declares success. */
    private String declaredFailure(CommunicationProperties.HttpApi config, String body) {
        String field = config.getSuccessField();
        if (field == null || field.isBlank()) {
            return null;
        }
        JsonNode root = parse(body);
        if (root == null) {
            return null;
        }
        JsonNode actual = root.at("/" + field.replace('.', '/'));
        if (actual.isMissingNode() || actual.isNull()) {
            return null;
        }
        String value = actual.asString();
        if (value.equalsIgnoreCase(orEmpty(config.getSuccessValue()))) {
            return null;
        }
        String errorField = config.getErrorField();
        JsonNode error = errorField == null ? null
                : root.at("/" + errorField.replace('.', '/'));
        return error != null && !error.isMissingNode() ? error.asString() : value;
    }

    private String reference(CommunicationProperties.HttpApi config, String body) {
        String field = config.getReferenceField();
        if (field != null && !field.isBlank()) {
            JsonNode root = parse(body);
            if (root != null) {
                JsonNode node = root.at("/" + field.replace('.', '/'));
                if (!node.isMissingNode() && !node.isNull()) {
                    return node.asString();
                }
            }
        }
        // No usable id in the response. A synthetic reference keeps correlation possible without
        // pretending the provider gave us one.
        return "HTTP-" + UUID.randomUUID();
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException unreadable) {
            // A non-JSON body from a 2xx is unusual but not fatal: the send still happened.
            return null;
        }
    }

    /**
     * Resolves the API key from the environment at send time.
     *
     * <p>Indirection through {@link Environment} rather than a bound field is deliberate: the key is
     * never stored in a configuration object, so it cannot be serialized into an actuator endpoint or
     * an error payload by accident.
     */
    private String apiKey(CommunicationProperties.HttpApi config) {
        String variable = config.getApiKeyEnv();
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String value = environment.getProperty(variable);
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public Optional<EmailSendResult> query(String providerReference) {
        // A generic adapter cannot assume the provider exposes a status lookup, and guessing an
        // endpoint would be worse than reporting none (§38).
        return Optional.empty();
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    /** Crude tag strip for the optional plain-text part; the HTML part is always sent as-is. */
    private static String stripTags(String html) {
        return html == null ? null : html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300);
    }
}
