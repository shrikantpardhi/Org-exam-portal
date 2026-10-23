package com.company.exam.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the JDBC statements Hibernate prepares, for query-count assertions (GAP-156).
 *
 * <p>Registered through {@code hibernate.session_factory.statement_inspector}, which requires a
 * public class with a no-argument constructor. Hibernate instantiates it once and calls
 * {@link #inspect(String)} for every statement, so collection is driven by {@link #start()} and
 * {@link #drain()} around the code under measurement rather than by the constructor.
 *
 * <p>Deliberately returns the SQL unchanged: an inspector that rewrote statements would change what
 * is being measured.
 */
public final class QueryCountInspector implements StatementInspector {

    private static final List<String> CAPTURED = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String inspect(String sql) {
        CAPTURED.add(sql);
        return sql;
    }

    /** Begins a measurement, discarding anything captured earlier. */
    public static void start() {
        CAPTURED.clear();
    }

    /** Ends a measurement and returns the statements captured since {@link #start()}. */
    public static List<String> drain() {
        synchronized (CAPTURED) {
            List<String> copy = new ArrayList<>(CAPTURED);
            CAPTURED.clear();
            return copy;
        }
    }
}
