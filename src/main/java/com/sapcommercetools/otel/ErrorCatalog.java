package com.sapcommercetools.otel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an SAP Commerce error-code taxonomy (for example the {@code VAL_*}
 * validation codes) into simple frequency analytics.
 *
 * <p>Codes are registered once with a human-readable description, then every
 * occurrence is fed in via {@link #record(String)}. {@link #topCodes(int)}
 * returns the most frequent codes, which is the generic core of the
 * "turn the VAL_* taxonomy into analytics" idea.
 *
 * <p>Recording a code that was never registered is allowed and still counted;
 * its description is reported as {@code "(unregistered)"} so nothing is lost.
 */
public final class ErrorCatalog {

    private static final String UNREGISTERED = "(unregistered)";

    private final Map<String, String> descriptions = new HashMap<>();
    private final Map<String, Long> counts = new HashMap<>();

    /**
     * Registers (or re-describes) an error code.
     *
     * @param code the error code, e.g. {@code "VAL_0046"} (non-blank)
     * @param description a human-readable description (non-null)
     * @return {@code this} for chaining
     */
    public ErrorCatalog register(String code, String description) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (description == null) {
            throw new IllegalArgumentException("description must not be null");
        }
        descriptions.put(code, description);
        return this;
    }

    /**
     * Records one occurrence of an error code, incrementing its counter.
     *
     * @param code the error code (non-blank)
     * @return the new count for that code
     */
    public long record(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return counts.merge(code, 1L, Long::sum);
    }

    /**
     * @param code an error code
     * @return the number of times the code has been recorded
     */
    public long count(String code) {
        return counts.getOrDefault(code, 0L);
    }

    /**
     * @param code an error code
     * @return the registered description, or {@code "(unregistered)"} when the
     *     code was recorded but never registered
     */
    public String description(String code) {
        return descriptions.getOrDefault(code, UNREGISTERED);
    }

    /**
     * Returns the {@code n} most frequently recorded codes, highest count first.
     * Ties are broken by code for a stable, deterministic order.
     *
     * @param n the maximum number of entries to return (a value &lt;= 0 yields
     *     an empty list)
     * @return an ordered list of {@link Entry}
     */
    public List<Entry> topCodes(int n) {
        if (n <= 0) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            entries.add(new Entry(e.getKey(), e.getValue(), description(e.getKey())));
        }
        entries.sort(Comparator
                .comparingLong(Entry::count).reversed()
                .thenComparing(Entry::code));
        return entries.size() <= n ? entries : new ArrayList<>(entries.subList(0, n));
    }

    /**
     * An aggregated error-code frequency row.
     *
     * @param code the error code
     * @param count how many times it was recorded
     * @param description its registered description (or {@code "(unregistered)"})
     */
    public record Entry(String code, long count, String description) {
    }
}
