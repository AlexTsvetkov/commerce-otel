package com.sapcommercetools.otel;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Factory that creates and enriches {@link Span}s with SAP Commerce
 * business-domain context.
 *
 * <p>Rather than depending on the OpenTelemetry SDK, this is a small, pure model
 * so the tracing behaviour is deterministic and unit-testable. Two suppliers are
 * injected:
 *
 * <ul>
 *   <li>{@code idSource} — a monotonically increasing source of ids. Ids are
 *       rendered as fixed-width hex so trace/span ids look realistic without
 *       relying on real randomness.</li>
 *   <li>{@code clock} — the time source (nanoseconds) used to stamp span start
 *       and end, making {@link Span#duration()} reproducible.</li>
 * </ul>
 *
 * <p>The no-arg constructor wires real defaults ({@link System#nanoTime()} and an
 * {@link AtomicLong}) for production use.
 */
public final class BusinessSpanFactory {

    /** Semantic-convention attribute key for the business scenario/use case. */
    public static final String ATTR_SCENARIO = "commerce.scenario";
    /** Semantic-convention attribute key for the sales channel (01/02/03). */
    public static final String ATTR_CHANNEL = "commerce.channel";
    /** Semantic-convention attribute key for the runway (TR/CR). */
    public static final String ATTR_RUNWAY = "commerce.runway";

    private final LongSupplier idSource;
    private final LongSupplier clock;

    /**
     * Production constructor: monotonic {@link AtomicLong} ids and
     * {@link System#nanoTime()} as the clock.
     */
    public BusinessSpanFactory() {
        this(new AtomicLong(1)::getAndIncrement, System::nanoTime);
    }

    /**
     * Deterministic constructor for tests.
     *
     * @param idSource a source of successive, distinct id values
     * @param clock a nanosecond time source
     */
    public BusinessSpanFactory(LongSupplier idSource, LongSupplier clock) {
        if (idSource == null || clock == null) {
            throw new IllegalArgumentException("idSource and clock must not be null");
        }
        this.idSource = idSource;
        this.clock = clock;
    }

    /**
     * Starts a new root span, allocating a fresh trace id and span id.
     *
     * @param name the operation name (non-blank)
     * @return the started root span
     */
    public Span startTrace(String name) {
        requireName(name);
        String traceId = nextId();
        String spanId = nextId();
        return new Span(traceId, spanId, null, name, clock.getAsLong());
    }

    /**
     * Starts a child span under {@code parent}, reusing the parent's trace id and
     * linking {@code parentSpanId} to the parent's span id.
     *
     * @param parent the parent span (non-null)
     * @param name the operation name (non-blank)
     * @return the started child span
     */
    public Span startChild(Span parent, String name) {
        if (parent == null) {
            throw new IllegalArgumentException("parent must not be null");
        }
        requireName(name);
        String spanId = nextId();
        return new Span(parent.traceId(), spanId, parent.spanId(), name, clock.getAsLong());
    }

    /**
     * Stamps the standardized business-domain attributes onto a span. Null
     * values are skipped so callers can supply only the dimensions they know.
     *
     * @param span the span to enrich (non-null)
     * @param scenario the business scenario (e.g. "renewal"); may be null
     * @param channel the sales channel (e.g. "01"); may be null
     * @param runway the runway (e.g. "CR"); may be null
     * @return the same {@code span} for chaining
     */
    public Span withDomain(Span span, String scenario, String channel, String runway) {
        if (span == null) {
            throw new IllegalArgumentException("span must not be null");
        }
        if (scenario != null) {
            span.setAttribute(ATTR_SCENARIO, scenario);
        }
        if (channel != null) {
            span.setAttribute(ATTR_CHANNEL, channel);
        }
        if (runway != null) {
            span.setAttribute(ATTR_RUNWAY, runway);
        }
        return span;
    }

    /**
     * Ends the span, stamping its end time from the clock.
     *
     * @param span the span to end (non-null)
     */
    public void end(Span span) {
        if (span == null) {
            throw new IllegalArgumentException("span must not be null");
        }
        span.close(clock.getAsLong());
    }

    private String nextId() {
        return String.format("%016x", idSource.getAsLong());
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
