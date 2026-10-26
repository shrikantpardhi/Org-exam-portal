package com.company.exam.communication.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.communication.config.CommunicationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

/**
 * The generic JSON-over-HTTP provider adapter (ADR-0049).
 *
 * <p>These tests run against a real {@link HttpServer} on a loopback port rather than a mocked client:
 * the value of this adapter is that an arbitrary provider's wire format can be configured, and that
 * claim is only proven by an actual request leaving the process and a real status code coming back.
 */
class HttpApiEmailGatewayTest {

    private static final String API_KEY = "test-key-12345";

    private HttpServer server;
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private int responseStatus;
    private String responseBody;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockEnvironment environment = new MockEnvironment();

    @BeforeEach
    void startStubProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/send", this::handle);
        server.start();
        environment.setProperty("EMAIL_API_KEY", API_KEY);
        received.clear();
    }

    @AfterEach
    void stopStubProvider() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        received.put("method", exchange.getRequestMethod());
        received.put("authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        received.put("apiKey", String.valueOf(exchange.getRequestHeaders().getFirst("X-Api-Key")));
        received.put("contentType", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
        received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private CommunicationProperties properties(String provider) {
        CommunicationProperties properties = new CommunicationProperties();
        properties.setProvider(provider);
        CommunicationProperties.HttpApi http = properties.getHttpApi();
        http.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/send");
        http.setApiKeyEnv("EMAIL_API_KEY");
        http.setBodyTemplate(
                "{\"to\":{to},\"from\":{from},\"subject\":{subject},\"html\":{html}}");
        return properties;
    }

    private HttpApiEmailGateway gateway(CommunicationProperties properties) {
        return new HttpApiEmailGateway(properties, objectMapper, environment);
    }

    private EmailGateway.RenderedEmail email() {
        return new EmailGateway.RenderedEmail("student@example.test", "Welcome", "<p>Hello</p>");
    }

    @Test
    void postsTheConfiguredJsonBodyToTheConfiguredUrl() {
        responseStatus = 202;
        responseBody = "{\"id\":\"provider-ref-1\"}";

        EmailGateway.EmailSendResult result = gateway(properties("HTTP_API")).send(email());

        assertThat(result.sent()).isTrue();
        assertThat(received.get("method")).isEqualTo("POST");
        assertThat(received.get("contentType")).contains("application/json");
        // The template is the contract with the provider: every placeholder must have been replaced.
        assertThat(received.get("body"))
                .contains("student@example.test")
                .contains("Welcome")
                .contains("<p>Hello</p>")
                .doesNotContain("{to}", "{subject}", "{html}");
    }

    @Test
    void sendsTheApiKeyAsABearerTokenByDefault() {
        responseStatus = 200;
        responseBody = "{}";

        gateway(properties("HTTP_API")).send(email());

        assertThat(received.get("authorization")).isEqualTo("Bearer " + API_KEY);
    }

    @Test
    void supportsAnApiKeyHeaderForProvidersThatUseOne() {
        // SendGrid and several others use a bespoke header rather than Authorization.
        responseStatus = 200;
        responseBody = "{}";
        CommunicationProperties properties = properties("HTTP_API");
        properties.getHttpApi().setAuthScheme("X_API_KEY");

        gateway(properties).send(email());

        assertThat(received.get("apiKey")).isEqualTo(API_KEY);
        assertThat(received.get("authorization")).isEqualTo("null");
    }

    @Test
    void aRateLimitIsRetryableButARejectedPayloadIsNot() {
        // The §34 split, which decides whether the bounded budget is spent.
        responseStatus = 429;
        responseBody = "{}";
        assertThat(gateway(properties("HTTP_API")).send(email()).retryable())
                .as("429 clears on its own").isTrue();

        responseStatus = 400;
        responseBody = "{\"message\":\"bad from address\"}";
        EmailGateway.EmailSendResult permanent = gateway(properties("HTTP_API")).send(email());
        assertThat(permanent.sent()).isFalse();
        assertThat(permanent.retryable()).as("400 will fail identically forever").isFalse();
    }

    @Test
    void aServerErrorIsRetryable() {
        responseStatus = 503;
        responseBody = "upstream unavailable";

        assertThat(gateway(properties("HTTP_API")).send(email()).retryable()).isTrue();
    }

    @Test
    void a2xxWithAnErrorEnvelopeIsStillAFailure() {
        // Some providers answer 200 with an error body; trusting the status alone would report a
        // dropped message as sent.
        responseStatus = 200;
        responseBody = "{\"status\":\"error\",\"message\":\"recipient suppressed\"}";
        CommunicationProperties properties = properties("HTTP_API");
        properties.getHttpApi().setSuccessField("status");

        EmailGateway.EmailSendResult result = gateway(properties).send(email());

        assertThat(result.sent()).isFalse();
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void readsTheProviderReferenceFromTheConfiguredField() {
        responseStatus = 200;
        responseBody = "{\"data\":{\"id\":\"msg_abc123\"}}";
        CommunicationProperties properties = properties("HTTP_API");
        properties.getHttpApi().setReferenceField("data.id");

        assertThat(gateway(properties).send(email()).providerReference()).isEqualTo("msg_abc123");
    }

    @Test
    void aMissingApiKeyIsPermanentAndNeverContactsTheProvider() {
        // Configuring the variable name but not setting the variable is an operator error; retrying
        // will not create the key.
        environment.setProperty("EMAIL_API_KEY", "");
        responseStatus = 200;
        responseBody = "{}";

        EmailGateway.EmailSendResult result = gateway(properties("HTTP_API")).send(email());

        assertThat(result.sent()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(received).as("no request should leave the process").isEmpty();
    }

    @Test
    void refusesToConstructWithoutAUrlOrBodyTemplate() {
        // A half-configured adapter must fail at boot, not spend the retry budget discovering it.
        CommunicationProperties noUrl = properties("HTTP_API");
        noUrl.getHttpApi().setUrl("");
        assertThatThrownBy(() -> gateway(noUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("url is required");

        CommunicationProperties noTemplate = properties("HTTP_API");
        noTemplate.getHttpApi().setBodyTemplate("");
        assertThatThrownBy(() -> gateway(noTemplate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("body-template is required");
    }

    @Test
    void htmlAndQuotesAreEscapedSoTheBodyStaysValidJson() {
        // A subject containing a quote must not break the provider's JSON parser.
        responseStatus = 200;
        responseBody = "{}";
        EmailGateway.RenderedEmail awkward = new EmailGateway.RenderedEmail("a@example.test",
                "Say \"hello\" now", "<p>line one</p>");

        gateway(properties("HTTP_API")).send(awkward);

        String body = received.get("body");
        assertThat(body).contains("Say \\\"hello\\\" now");
        // And the whole document still parses as JSON.
        assertThat(objectMapper.readTree(body).get("subject").asString())
                .isEqualTo("Say \"hello\" now");
    }

    @Test
    void theProviderIdIsConfigurableSoAnyAdapterNameCanBeSelected() {
        CommunicationProperties properties = properties("MY_PROVIDER");
        properties.getHttpApi().setProviderId("MY_PROVIDER");

        assertThat(gateway(properties).provider().value()).isEqualTo("MY_PROVIDER");
    }

    @Test
    void queryReturnsEmptyBecauseAGenericAdapterCannotGuessAStatusEndpoint() {
        assertThat(gateway(properties("HTTP_API")).query("anything")).isEmpty();
    }

    @Test
    void thePlainTextPartStripsTags() {
        responseStatus = 200;
        responseBody = "{}";
        CommunicationProperties properties = properties("HTTP_API");
        properties.getHttpApi().setBodyTemplate("{\"text\":{text}}");

        gateway(properties).send(email());

        assertThat(received.get("body")).contains("Hello").doesNotContain("<p>");
    }
}