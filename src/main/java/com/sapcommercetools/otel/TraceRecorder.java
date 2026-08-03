package com.sapcommercetools.otel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects {@link Span}s and reassembles them per trace.
 *
 * <p>In a real deployment spans arrive out of order and interleaved across
 * services (monolith, BTP microservices, lambdas, eventing hops). This recorder
 * keeps them grouped by trace id and can produce a stable, parent-before-child
 * ordering suitable for rendering a call tree.
 */
public final class TraceRecorder {

    /** trace id -> spans in arrival order. */
    private final Map<String, List<Span>> byTrace = new LinkedHashMap<>();

    /**
     * Records a span for later assembly.
     *
     * @param span the span to record (non-null)
     */
    public void record(Span span) {
        if (span == null) {
            throw new IllegalArgumentException("span must not be null");
        }
        byTrace.computeIfAbsent(span.traceId(), k -> new ArrayList<>()).add(span);
    }

    /**
     * @param traceId a trace id
     * @return the spans recorded for the trace in arrival order (unmodifiable);
     *     empty when the trace is unknown
     */
    public List<Span> spansForTrace(String traceId) {
        List<Span> spans = byTrace.get(traceId);
        return spans == null ? List.of() : Collections.unmodifiableList(spans);
    }

    /**
     * Assembles a trace into a stable order where every parent appears before
     * its children (a depth-first pre-order walk of the span tree).
     *
     * <p>Roots (spans whose parent is {@code null} or not present in the trace)
     * are emitted in arrival order; children are emitted under their parent in
     * arrival order. Any spans left unreachable due to a broken parent link are
     * appended at the end so nothing is silently dropped.
     *
     * @param traceId the trace id
     * @return an ordered, possibly empty list of spans
     */
    public List<Span> assemble(String traceId) {
        List<Span> spans = byTrace.get(traceId);
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }

        // Index spans by their own id, and group children by parent id, both
        // preserving arrival order.
        Map<String, Span> byId = new HashMap<>();
        Map<String, List<Span>> childrenOf = new LinkedHashMap<>();
        List<Span> roots = new ArrayList<>();

        for (Span s : spans) {
            byId.put(s.spanId(), s);
        }
        for (Span s : spans) {
            String parent = s.parentSpanId();
            if (parent == null || !byId.containsKey(parent)) {
                roots.add(s);
            } else {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(s);
            }
        }

        List<Span> ordered = new ArrayList<>(spans.size());
        Deque<Span> stack = new ArrayDeque<>();
        // Push roots in reverse so they are visited in arrival order.
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(roots.get(i));
        }
        while (!stack.isEmpty()) {
            Span current = stack.pop();
            ordered.add(current);
            List<Span> kids = childrenOf.get(current.spanId());
            if (kids != null) {
                for (int i = kids.size() - 1; i >= 0; i--) {
                    stack.push(kids.get(i));
                }
            }
        }

        // Safety net: append anything not reached (e.g. a parent cycle).
        if (ordered.size() != spans.size()) {
            for (Span s : spans) {
                if (!ordered.contains(s)) {
                    ordered.add(s);
                }
            }
        }

        return ordered;
    }

    /**
     * Counts how many spans in a trace carry a given attribute value. Useful for
     * simple analytics such as "how many hops were on channel 01".
     *
     * @param traceId the trace id
     * @param attributeKey the attribute key to inspect
     * @param value the value to match
     * @return the number of matching spans
     */
    public long countByAttribute(String traceId, String attributeKey, String value) {
        return spansForTrace(traceId).stream()
                .filter(s -> value != null && value.equals(s.attributes().get(attributeKey)))
                .count();
    }

    /**
     * @return the set of trace ids seen so far, in first-seen order.
     */
    public List<String> traceIds() {
        return new ArrayList<>(byTrace.keySet());
    }
}
