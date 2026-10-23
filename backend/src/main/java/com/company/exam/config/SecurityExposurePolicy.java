package com.company.exam.config;

import com.company.exam.identity.service.AuthorizationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single decision point for what security-sensitive infrastructure surface is reachable, and
 * which security headers describe it ({@code specs/21-P12-Hardening-V1.5.md §26}; closes GAP-136,
 * GAP-137, and GAP-138).
 *
 * <p><b>Why a dedicated policy object.</b> Three separate mechanisms need the same answer — the
 * filter chain (which paths are public), the CSP value (which differs for an HTML surface and a
 * JSON one), and the tests. Duplicating the environment logic across them is how the two
 * disagreed in the first place. Everything reads this one component.
 *
 * <p><b>Production is decided by two independent signals.</b> {@code app.environment} is the
 * declared environment; the active Spring profiles are what the process was actually started
 * with. Either alone is defeatable in practice: {@code docker-compose.yml} sets an
 * {@code APP_ENVIRONMENT} variable that outranks the value in a profile-specific YAML file, so a
 * process started with {@code --spring.profiles.active=prod} inside a container whose environment
 * still says {@code staging} declares itself non-production while running the production profile.
 * That combination shipped a defect once already (GAP-085). It is treated as production here.
 */
@Component("securityExposurePolicy")
public class SecurityExposurePolicy {

    /** The permission that already governs the aggregated administrative health view. */
    static final String SYSTEM_HEALTH_PERMISSION = "SYSTEM_HEALTH_VIEW";

    /** Authority granted to a caller that presents the monitoring bearer token. */
    public static final String MONITORING_SCOPE = "MONITORING_SCOPE";

    private final Environment environment;
    private final ApiDocsExposureProperties apiDocsProperties;
    private final MonitoringProperties monitoringProperties;
    private final AuthorizationService authorizationService;

    public SecurityExposurePolicy(Environment environment,
            ApiDocsExposureProperties apiDocsProperties,
            MonitoringProperties monitoringProperties,
            AuthorizationService authorizationService) {
        this.environment = environment;
        this.apiDocsProperties = apiDocsProperties;
        this.monitoringProperties = monitoringProperties;
        this.authorizationService = authorizationService;
    }

    /** The declared environment ({@code app.environment}), for messages and metrics. */
    public String declaredEnvironment() {
        return environment.getProperty("app.environment", "unknown");
    }

    /** The Spring profiles this process is actually running with. */
    public List<String> activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles == null ? List.of() : List.of(profiles);
    }

    /**
     * Whether this process is production, judged by the declared environment <em>or</em> by an
     * active production profile.
     *
     * <p>Accepts both the short ({@code prod}) and long ({@code production}) spellings on both
     * signals, so a deployment cannot escape the gate by naming the environment differently.
     */
    public boolean isProduction() {
        String declared = declaredEnvironment();
        if ("production".equalsIgnoreCase(declared) || "prod".equalsIgnoreCase(declared)) {
            return true;
        }
        for (String profile : activeProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the API documentation may be served to an unauthenticated caller.
     *
     * <p>Both an explicit switch and the environment must agree. The switch alone is advisory:
     * flipping {@code app.security.exposure.api-docs-enabled=true} in a production deployment
     * does not publish the API surface, because production refuses regardless. That is deliberate
     * — a documentation aid is not worth a misconfiguration becoming an incident.
     */
    public boolean apiDocsReachable() {
        return apiDocsProperties.isApiDocsEnabled() && !isProduction();
    }

    /**
     * Whether the monitoring token is configured. When it is not, the actuator surface falls back
     * to requiring a normal authenticated principal and never becomes anonymous.
     */
    public boolean monitoringTokenConfigured() {
        return !monitoringProperties.getMetricsToken().isBlank();
    }

    /**
     * Constant-time comparison of a presented bearer token against the configured monitoring token.
     *
     * <p>{@link MessageDigest#isEqual} avoids the early-exit byte comparison that would otherwise
     * leak the token's prefix length through response timing. An unconfigured token never matches,
     * so a deployment that forgets to set one fails closed rather than accepting an empty string.
     */
    public boolean matchesMonitoringToken(String presented) {
        if (presented == null || !monitoringTokenConfigured()) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                monitoringProperties.getMetricsToken().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Authorization decision for the actuator surface beyond health.
     *
     * <p>Two routes are accepted, because the two legitimate callers are different kinds of
     * principal:
     *
     * <ul>
     *   <li>the scrape job, which holds the monitoring token and therefore the
     *       {@link #MONITORING_SCOPE} authority — a machine credential scoped to metrics only;</li>
     *   <li>an operator holding {@code SYSTEM_HEALTH_VIEW}, the permission that already governs the
     *       administrative health view, so a human can inspect metrics without a second credential
     *       system.</li>
     * </ul>
     *
     * <p>Anything else is denied. In particular a valid end-user JWT without that permission is
     * denied: being logged in is not the same as being allowed to read operational internals.
     */
    public boolean mayReadActuator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean monitoringScope = authentication.getAuthorities().stream()
                .anyMatch(authority -> MONITORING_SCOPE.equals(authority.getAuthority()));
        if (monitoringScope) {
            return true;
        }
        return authorizationService.hasPermission(authentication, SYSTEM_HEALTH_PERMISSION);
    }

    /**
     * The Content-Security-Policy for this process.
     *
     * <p>The backend answers with JSON, so in production nothing needs to be loadable at all and
     * the policy is the most restrictive one that is still meaningful: {@code default-src 'none'}
     * forbids every fetch directive at once, and the remaining directives close the framing, base
     * URI, and form-submission escapes.
     *
     * <p>Outside production the same process also serves Swagger UI, which is an HTML page that
     * ships inline bootstrap script and styles. Serving the production policy there would break
     * the local developer experience the constraint requires us to preserve, so a policy that
     * permits only same-origin resources plus the inline blocks Swagger UI actually emits is used
     * instead. It is still a real policy: no external script origin, no framing, no form escape.
     */
    public String contentSecurityPolicy() {
        if (isProduction()) {
            return "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
        }
        return "default-src 'self'; "
                + "script-src 'self' 'unsafe-inline'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "font-src 'self' data:; "
                + "connect-src 'self'; "
                + "frame-ancestors 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self'";
    }
}
