package com.sapcommercetools.otel.examples;

import com.sapcommercetools.otel.BusinessSpanFactory;
import com.sapcommercetools.otel.ErrorCatalog;
import com.sapcommercetools.otel.Span;
import com.sapcommercetools.otel.TraceRecorder;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A runnable, self-contained mini-tutorial for <b>commerce-otel</b>.
 *
 * <p>It shows the three moving parts of the library working together, using
 * DETERMINISTIC injected sources so the printed ids and durations are stable
 * every run (great for docs and tests):
 *
 * <ol>
 *   <li>{@link BusinessSpanFactory} — starts a root span and child spans,
 *       stamps SAP-Commerce business attributes (scenario / channel / runway),
 *       and ends them against an injected clock.</li>
 *   <li>{@link TraceRecorder} — collects spans that arrive out of order and
 *       reassembles them into a parent-before-child tree.</li>
 *   <li>{@link ErrorCatalog} — registers {@code VAL_*} validation codes and
 *       turns recorded occurrences into {@code topCodes()} frequency
 *       analytics.</li>
 * </ol>
 *
 * <p>Run it with:
 * <pre>{@code
 * find src/main/java -name '*.java' | xargs javac -d /tmp/ex-otel
 * java -cp /tmp/ex-otel com.sapcommercetools.otel.examples.Example
 * }</pre>
 */
public final class Example {

    private Example() {
    }

    public static void main(String[] args) {
        section("1. Start a trace + child spans with deterministic clock/id sources");

        // The 2-arg constructor lets us inject:
        //  - an idSource: successive ids -> rendered as fixed-width hex, so the
        //    first trace id is ...0001, first span ...0002, etc.
        //  - a clock (nanoseconds): here a fake clock that advances 1_000_000 ns
        //    (= 1 ms) on every read, so span durations are exact and repeatable.
        AtomicLong ids = new AtomicLong(1);
        AtomicLong fakeNanos = new AtomicLong(0);
        BusinessSpanFactory factory = new BusinessSpanFactory(
                ids::getAndIncrement,
                () -> fakeNanos.getAndAdd(1_000_000L));

        // A TraceRecorder collects every span we create for later assembly.
        TraceRecorder recorder = new TraceRecorder();

        // Root span: a whole "renewal" business flow on channel 01, cloud runway.
        Span root = factory.startTrace("checkout-renewal");
        factory.withDomain(root, "renewal", "01", "CR");
        recorder.record(root);

        // Child span 1: validate the cart. It inherits the root's trace id and
        // links back to the root's span id as its parent.
        Span validate = factory.startChild(root, "validate-cart");
        factory.withDomain(validate, "renewal", "01", "CR");
        recorder.record(validate);

        // A grandchild under 'validate': call the pricing service (CPQ).
        Span pricing = factory.startChild(validate, "cpq-pricing-call");
        factory.withDomain(pricing, "renewal", "01", "CR");
        recorder.record(pricing);

        // Child span 2 (a sibling of 'validate'): persist the order.
        Span persist = factory.startChild(root, "persist-order");
        factory.withDomain(persist, "renewal", "01", "CR");
        recorder.record(persist);

        // End the spans (innermost first, as a real call stack would unwind).
        // Each end() stamps the current clock value, giving each span a
        // deterministic, non-zero duration.
        factory.end(pricing);
        factory.end(validate);
        factory.end(persist);
        factory.end(root);

        System.out.println("Created trace id: " + root.traceId());
        System.out.println("Root span id:     " + root.spanId()
                + "  (parent=" + root.parentSpanId() + ")");
        System.out.println("Domain attrs on root: " + root.attributes());

        section("2. Assemble + print the span tree (parent before child)");
        // assemble() returns a depth-first, parent-before-child ordering even
        // though spans may have been recorded in any order. We compute indent
        // depth by walking each span's parent chain.
        String traceId = root.traceId();
        List<Span> ordered = recorder.assemble(traceId);
        for (Span s : ordered) {
            int depth = depthOf(s, ordered);
            String indent = "  ".repeat(depth);
            double ms = s.duration() / 1_000_000.0;
            System.out.printf("%s- %-18s [span=%s] %.1f ms %s%n",
                    indent, s.name(), s.spanId(), ms, s.attributes());
        }

        // TraceRecorder can also answer simple analytics questions about a trace.
        long channel01 = recorder.countByAttribute(traceId, "commerce.channel", "01");
        System.out.println("Spans on channel 01: " + channel01 + " of " + ordered.size());

        section("3. Turn the VAL_* taxonomy into analytics with ErrorCatalog");

        // Register a few validation codes with human-readable descriptions.
        ErrorCatalog catalog = new ErrorCatalog()
                .register("VAL_0046", "Quote total below the channel minimum")
                .register("VAL_0102", "Renewal end-date precedes start-date")
                .register("VAL_0210", "Configurable product missing required characteristic");

        // Now record occurrences as they would stream in from a running system.
        // (VAL_9999 is recorded but never registered — it will still be counted,
        // and reported with a "(unregistered)" description so nothing is lost.)
        record(catalog, "VAL_0046", 5);
        record(catalog, "VAL_0102", 2);
        record(catalog, "VAL_0210", 8);
        record(catalog, "VAL_9999", 1);

        System.out.println("Top 3 error codes by frequency:");
        for (ErrorCatalog.Entry e : catalog.topCodes(3)) {
            System.out.printf("  %-9s x%-3d  %s%n", e.code(), e.count(), e.description());
        }
        System.out.println("Direct count for VAL_0046: " + catalog.count("VAL_0046"));
        System.out.println("Description for unregistered VAL_9999: "
                + catalog.description("VAL_9999"));

        System.out.println();
        System.out.println("Done.");
    }

    /** Records {@code times} occurrences of a code (a small convenience loop). */
    private static void record(ErrorCatalog catalog, String code, int times) {
        for (int i = 0; i < times; i++) {
            catalog.record(code);
        }
    }

    /** Computes a span's depth by following parent links within the trace. */
    private static int depthOf(Span span, List<Span> spans) {
        int depth = 0;
        String parentId = span.parentSpanId();
        while (parentId != null) {
            Span parent = findById(spans, parentId);
            if (parent == null) {
                break;
            }
            depth++;
            parentId = parent.parentSpanId();
        }
        return depth;
    }

    private static Span findById(List<Span> spans, String spanId) {
        for (Span s : spans) {
            if (s.spanId().equals(spanId)) {
                return s;
            }
        }
        return null;
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
