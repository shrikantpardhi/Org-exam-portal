package com.company.exam.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;

/**
 * Starts a single disposable PostgreSQL server for the test JVM.
 *
 * <p>This is the documented alternative to Testcontainers for this repository
 * ({@code docs/decisions/ADR-0009-integration-test-datastore.md}): the P0 development host and the
 * CI runner use a real, embedded PostgreSQL instead of a Docker-managed container, so integration
 * tests exercise genuine PostgreSQL behaviour (extensions, JSONB, {@code TIMESTAMPTZ}) and remain
 * deterministic without a pre-running database.
 */
public final class EmbeddedPostgresSupport {

    private static volatile EmbeddedPostgres instance;

    private EmbeddedPostgresSupport() {
    }

    public static EmbeddedPostgres getInstance() {
        if (instance == null) {
            synchronized (EmbeddedPostgresSupport.class) {
                if (instance == null) {
                    instance = start();
                    Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedPostgresSupport::stop,
                            "embedded-postgres-shutdown"));
                }
            }
        }
        return instance;
    }

    public static String jdbcUrl() {
        return getInstance().getJdbcUrl("postgres", "postgres");
    }

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to start embedded PostgreSQL for tests", failure);
        }
    }

    private static void stop() {
        EmbeddedPostgres current = instance;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // JVM is shutting down; nothing actionable remains.
            }
        }
    }
}
