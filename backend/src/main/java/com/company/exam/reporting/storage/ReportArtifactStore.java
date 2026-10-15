package com.company.exam.reporting.storage;

import com.company.exam.common.exception.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The protected report artifact store ({@code specs/22-P13-Reporting-V1.5.md §21-§23, §32}).
 *
 * <p>Artifacts are written under the configured root in a per-job directory using a
 * <strong>deterministic, sanitized</strong> name derived from the report type and filters — a raw
 * user-provided string is never used as a storage path (§32). The store returns an opaque relative
 * key; a filesystem path, bucket credential, or permanent public URL is never exposed (§22).
 *
 * <p>Download is protected two ways (§23): the caller must own the job (or hold an operational
 * permission), and the short-lived signed token must be valid and scoped to the exact artifact.
 */
@Component
public class ReportArtifactStore {

    private final Path root;
    private final Duration tokenLifetime;
    private final byte[] signingKey;

    public ReportArtifactStore(@Value("${app.reporting.artifact-root:./data/reports}") Path root,
            @Value("${app.reporting.download-token-lifetime:PT15M}") Duration tokenLifetime,
            @Value("${app.reporting.download-signing-key:}") String signingKey) {
        this.root = root.toAbsolutePath().normalize();
        this.tokenLifetime = tokenLifetime;
        // An empty key still produces a deterministic local key; a real deployment supplies one.
        this.signingKey = (signingKey == null || signingKey.isBlank() ? "reporting-local-key" : signingKey)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Writes the artifact and returns its opaque storage key. */
    public String write(UUID jobId, String safeFileName, byte[] content) {
        String key = jobId + "/" + sanitize(safeFileName);
        Path target = resolveInsideRoot(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return key;
        } catch (IOException failure) {
            throw new IllegalStateException("Report artifact could not be stored");
        }
    }

    public byte[] read(String storageKey) {
        Path target = resolveInsideRoot(storageKey);
        try {
            return Files.readAllBytes(target);
        } catch (IOException missing) {
            throw NotFoundException.of("Report artifact", storageKey);
        }
    }

    public boolean exists(String storageKey) {
        try {
            return Files.exists(resolveInsideRoot(storageKey));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    /** A short-lived token scoped to exactly one artifact ({@code §23}). */
    public String issueToken(UUID jobId) {
        long expiresAt = Instant.now().plus(tokenLifetime).getEpochSecond();
        String payload = jobId + ":" + expiresAt;
        return payload + ":" + sign(payload);
    }

    /** Verifies the token signature and expiry, returning the artifact it authorizes. */
    public UUID verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split(":");
        if (parts.length != 3) {
            return null;
        }
        String payload = parts[0] + ":" + parts[1];
        if (!sign(payload).equals(parts[2])) {
            return null;
        }
        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(parts[1])) {
                return null;
            }
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * Keeps resolved paths inside the root and refuses traversal, so an artifact key can never read
     * or write outside the store.
     */
    private Path resolveInsideRoot(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw NotFoundException.of("Report artifact", storageKey);
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw NotFoundException.of("Report artifact", storageKey);
        }
        return resolved;
    }

    /** Lower-cases and strips anything that is not alphanumeric, dash, dot, or underscore (§32). */
    private String sanitize(String fileName) {
        String sanitized = fileName == null ? "report" : fileName.replaceAll("[^A-Za-z0-9._-]", "-");
        sanitized = sanitized.replace("..", "-");
        return sanitized.isBlank() ? "report" : sanitized;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Report download token could not be signed");
        }
    }
}
