package com.company.exam.identity.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-shot command-line entry point for SUPER_ADMIN bootstrap (ADR-0032, ADR-0033).
 *
 * <p>This is how the bootstrap shell script invokes the mechanism. It is a separate {@code main} inside
 * the application jar — not a controller, not an endpoint — so it adds no HTTP surface. It boots a
 * non-web Spring context, creates the installation owner once, writes the result to stdout, and exits.
 *
 * <p>The credential is read from stdin as {@code {"email","password"}} (the shell script supplies it from
 * {@code SUPER_ADMIN_EMAIL}/{@code SUPER_ADMIN_PASSWORD}), so the password never appears in a process
 * argument list. It still requires {@code --app.super-admin.bootstrap.enabled=true} plus a non-production
 * {@code app.environment}.
 */
public final class SuperAdminBootstrapCli {

    static final String RESULT_MARKER = "SUPER_ADMIN_BOOTSTRAP_RESULT ";

    private SuperAdminBootstrapCli() {
    }

    public static void main(String[] args) throws IOException {
        int exitCode = run(args, System.in, System.out);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs one bootstrap pass.
     *
     * @return {@code 0} on success, {@code 2} on a refused request, {@code 3} on an unexpected failure
     */
    static int run(String[] args, InputStream in, PrintStream out) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(in.readAllBytes());
        String email = root == null ? null : root.path("email").asText(null);
        String password = root == null ? null : root.path("password").asText(null);
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            out.println(RESULT_MARKER + "{\"error\":\"email and password are required on stdin\"}");
            return 2;
        }

        String[] effective = withEnablingProperty(args);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                com.company.exam.ExamPlatformApplication.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .run(effective)) {
            SuperAdminBootstrapService service = context.getBean(SuperAdminBootstrapService.class);
            SuperAdminBootstrapDtos.BootstrapResult result = service.bootstrap(email, password);
            out.println(RESULT_MARKER + toJson(mapper, result));
            return 0;
        } catch (SuperAdminBootstrapDisabledException refused) {
            out.println(RESULT_MARKER + mapper.createObjectNode().put("error", refused.getMessage()));
            return 2;
        } catch (RuntimeException failure) {
            out.println(RESULT_MARKER + mapper.createObjectNode().put("error",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            return 3;
        }
    }

    /**
     * Applies the CLI's own property overrides: the bootstrap property, plus every background worker
     * switched off so the single short transaction exits deterministically.
     */
    private static String[] withEnablingProperty(String[] args) {
        List<String> effective = new ArrayList<>(List.of(args));
        addIfAbsent(effective, "--app.super-admin.bootstrap.enabled=", "true");
        addIfAbsent(effective, "--app.delivery.worker.enabled=", "false");
        addIfAbsent(effective, "--app.evaluation.worker.enabled=", "false");
        addIfAbsent(effective, "--app.analytics.worker.enabled=", "false");
        addIfAbsent(effective, "--app.communication.worker.enabled=", "false");
        addIfAbsent(effective, "--app.reporting.worker.enabled=", "false");
        return effective.toArray(new String[0]);
    }

    private static void addIfAbsent(List<String> effective, String prefix, String value) {
        boolean present = effective.stream().anyMatch(a -> a.startsWith(prefix));
        if (!present) {
            effective.add(prefix + value);
        }
    }

    private static String toJson(ObjectMapper mapper, SuperAdminBootstrapDtos.BootstrapResult result)
            throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("outcome", result.outcome());
        root.put("userId", result.userId() == null ? null : result.userId().toString());
        return mapper.writeValueAsString(root);
    }
}
