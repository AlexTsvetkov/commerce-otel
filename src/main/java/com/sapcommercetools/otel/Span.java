package com.sapcommercetools.otel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A minimal, dependency-free model of a tracing span.
 *
 * <p>This deliberately mirrors the shape of an OpenTelemetry span (trace id,
 * span id, parent link, name, attributes, start/end timestamps) but carries no
 * OpenTelemetry dependency, so the business-tracing logic can be unit-tested in
 * isolation. Timestamps are captured in nanoseconds from an injected clock so
 * that {@link #duration()} is fully deterministic in tests.
 *
 * <p>A span is mutable while it is being recorded (attributes may be added and
 * it is eventually ended) but its identity fields are immutable.
 */
public final class Span {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final long startNanos;

    private long endNanos;
    private boolean ended;

    Span(String traceId, String spanId, String parentSpanId, String name, long startNanos) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.startNanos = startNanos;
    }

    /** @return the id shared by every span of one end-to-end business trace. */
    public String traceId() {
        return traceId;
    }

    /** @return this span's unique id within the trace. */
    public String spanId() {
        return spanId;
    }

    /** @return the parent span's id, or {@code null} when this is a root span. */
    public String parentSpanId() {
        return parentSpanId;
    }

    /** @return the operation name. */
    public String name() {
        return name;
    }

    /** @return an unmodifiable view of the span's attributes. */
    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /** @return the start timestamp (nanoseconds, from the injected clock). */
    public long startNanos() {
        return startNanos;
    }

    /** @return the end timestamp, or {@code 0} while the span is still open. */
    public long endNanos() {
        return endNanos;
    }

    /** @return {@code true} once {@link BusinessSpanFactory#end(Span)} ran. */
    public boolean isEnded() {
        return ended;
    }

    /**
     * @return elapsed nanoseconds between start and end; {@code 0} if the span
     *     has not been ended yet.
     */
    public long duration() {
        return ended ? endNanos - startNanos : 0L;
    }

    /**
     * Sets or overwrites a single attribute. Package-private: callers go through
     * {@link BusinessSpanFactory} so semantic conventions stay consistent.
     */
    void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    /** Marks the span ended at {@code endNanos}. Idempotent-safe callers only. */
    void close(long endNanos) {
        this.endNanos = endNanos;
        this.ended = true;
    }

    @Override
    public String toString() {
        return "Span{trace=" + traceId + ", span=" + spanId + ", parent=" + parentSpanId
                + ", name=" + name + ", ended=" + ended + '}';
    }
}
