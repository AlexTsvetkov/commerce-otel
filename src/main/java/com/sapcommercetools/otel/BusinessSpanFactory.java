package com.sapcommercetools.otel;

/**
 * Creates spans enriched with SAP Commerce domain attributes (scenario, channel, runway) and links async NATS hops back to the originating business transaction.
 *
 * <p>This is the core abstraction of <b>commerce-otel</b>. The starter implementation
 * below is intentionally minimal — a foundation that documents the intended
 * contract and gives tests something real to exercise.
 */
public final class BusinessSpanFactory {

    /**
     * Returns a human-readable description of what this component does.
     * Replace with the real behaviour as the project grows.
     */
    public String describe() {
        return "commerce-otel: Business-level distributed tracing and error-taxonomy analytics for SAP Commerce topologies (monolith + BTP microservices + lambdas + eventing).";
    }

    /**
     * Placeholder for the primary operation. Kept trivial and total so the
     * scaffold builds and tests pass on a clean checkout.
     *
     * @param input a caller-supplied token
     * @return {@code true} when the input is non-blank
     */
    public boolean accepts(String input) {
        return input != null && !input.isBlank();
    }
}
