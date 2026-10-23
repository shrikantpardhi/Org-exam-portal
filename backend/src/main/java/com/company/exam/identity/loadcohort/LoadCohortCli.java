package com.company.exam.identity.loadcohort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One-shot command-line entry point for synthetic load-cohort provisioning (GAP-085).
 *
 * <p>This is how the load seed script invokes the mechanism. It is a separate {@code main} inside the
 * application jar, not a controller and not an endpoint, so it adds <b>no HTTP surface at all</b>: there
 * is no mapping to authenticate against and nothing for the security filter chain to permit. It boots a
 * non-web Spring context, provisions once, writes the result to stdout, and exits.
 *
 * <p>It is only reachable by someone who can already execute a process inside the container — that is,
 * someone with deployment access — and it still requires {@code --app.load-cohort.enabled=true} plus a
 * non-production {@code app.environment}.
 *
 * <p>Usage, from the seed script:
 *
 * <pre>{@code
 * java -cp app.jar com.company.exam.identity.loadcohort.LoadCohortCli \
 *      --app.load-cohort.enabled=true --spring.profiles.active=staging
 * }</pre>
 *
 * <p>The member list is read from stdin as a JSON array of
 * {@code {"email","firstName","lastName"}} objects, and the result is written to stdout as JSON with the
 * generated credentials. Credentials are never written to logs or to the database in plaintext.
 */
public final class LoadCohortCli {

    /**
     * Prefix written before the single result document on stdout.
     *
     * <p>Spring's structured logging also emits JSON on stdout, including shutdown lines that arrive
     * <em>after</em> the result. A caller that simply took "the last JSON line" would therefore read a log
     * record instead of the result, so the payload is marked explicitly.
     */
    static final String RESULT_MARKER = "LOAD_COHORT_RESULT ";

    private LoadCohortCli() {
    }

    public static void main(String[] args) throws IOException {
        int exitCode = run(args, System.in, System.out);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs one provisioning pass.
     *
     * @return {@code 0} on success, {@code 2} on a refused request, {@code 3} on an unexpected failure
     */
    static int run(String[] args, InputStream in, PrintStream out) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<LoadCohortDtos.CohortMember> members = readMembers(mapper, in);
        if (members.isEmpty()) {
            out.println(RESULT_MARKER + "{\"error\":\"no members supplied on stdin\"}");
            return 2;
        }

        // Force the enabling property on: the CLI is the opt-in. Everything else (environment gate,
        // bounds, hashing, idempotency) still applies exactly as it does for any other caller.
        String[] effective = withEnablingProperty(args);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                com.company.exam.ExamPlatformApplication.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .run(effective)) {

            LoadCohortProvisioningService service = context.getBean(LoadCohortProvisioningService.class);
            LoadCohortDtos.ProvisioningResult result = service.provision(members);
            out.println(RESULT_MARKER + toJson(mapper, result));
            return 0;
        } catch (LoadCohortDisabledException refused) {
            // Refusal is an expected, reportable outcome, not a crash.
            out.println(RESULT_MARKER + mapper.createObjectNode().put("error", refused.getMessage()));
            return 2;
        } catch (RuntimeException failure) {
            out.println(RESULT_MARKER + mapper.createObjectNode().put("error",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            return 3;
        }
    }

    /**
     * Applies the CLI's own property overrides.
     *
     * <p>The provisioning pass is a single short transaction, so every background worker is switched off:
     * leaving the snapshot/evaluation/analytics workers running would let unrelated scheduled work start
     * against a partially provisioned cohort, and would make the exit non-deterministic.
     */
    private static String[] withEnablingProperty(String[] args) {
        List<String> effective = new ArrayList<>(List.of(args));
        addIfAbsent(effective, "--app.load-cohort.enabled=", "true");
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

    private static List<LoadCohortDtos.CohortMember> readMembers(ObjectMapper mapper, InputStream in)
            throws IOException {
        JsonNode root = mapper.readTree(in.readAllBytes());
        List<LoadCohortDtos.CohortMember> members = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return members;
        }
        for (JsonNode node : root) {
            members.add(new LoadCohortDtos.CohortMember(
                    node.path("email").asText(null),
                    node.path("firstName").asText("Load"),
                    node.path("lastName").asText("Student")));
        }
        return members;
    }

    private static String toJson(ObjectMapper mapper, LoadCohortDtos.ProvisioningResult result)
            throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("created", result.created());
        root.put("updated", result.updated());
        root.put("skipped", result.skipped());
        ArrayNode members = root.putArray("members");
        for (LoadCohortDtos.MemberOutcome outcome : result.members()) {
            ObjectNode node = members.addObject();
            node.put("email", outcome.email());
            node.put("userId", outcome.userId() == null ? null : outcome.userId().toString());
            node.put("credential", outcome.credential());
            node.put("action", outcome.action());
        }
        return mapper.writeValueAsString(root);
    }
}
