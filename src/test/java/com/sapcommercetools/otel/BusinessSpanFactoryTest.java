package com.sapcommercetools.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class BusinessSpanFactoryTest {

    /** Ids increment 1,2,3...; the clock advances by 100ns per read. */
    private BusinessSpanFactory deterministicFactory() {
        LongSupplier ids = new AtomicLong(1)::getAndIncrement;
        AtomicLong tick = new AtomicLong(0);
        LongSupplier clock = () -> tick.getAndAdd(100);
        return new BusinessSpanFactory(ids, clock);
    }

    @Test
    void trace_and_child_share_trace_id_and_link_parent() {
        BusinessSpanFactory f = deterministicFactory();
        Span root = f.startTrace("checkout");
        Span child = f.startChild(root, "price-lookup");

        assertEquals(root.traceId(), child.traceId());
        assertEquals(root.spanId(), child.parentSpanId());
        assertNull(root.parentSpanId());
        assertFalse(root.spanId().equals(child.spanId()));
    }

    @Test
    void ids_are_fixed_width_hex() {
        BusinessSpanFactory f = deterministicFactory();
        Span root = f.startTrace("checkout");
        assertEquals(16, root.traceId().length());
        assertEquals(16, root.spanId().length());
    }

    @Test
    void domain_attributes_use_convention_keys() {
        BusinessSpanFactory f = deterministicFactory();
        Span span = f.startTrace("quote");
        f.withDomain(span, "renewal", "02", "CR");

        assertEquals("renewal", span.attributes().get(BusinessSpanFactory.ATTR_SCENARIO));
        assertEquals("02", span.attributes().get(BusinessSpanFactory.ATTR_CHANNEL));
        assertEquals("CR", span.attributes().get(BusinessSpanFactory.ATTR_RUNWAY));
        assertEquals("commerce.scenario", BusinessSpanFactory.ATTR_SCENARIO);
    }

    @Test
    void domain_skips_null_dimensions() {
        BusinessSpanFactory f = deterministicFactory();
        Span span = f.startTrace("quote");
        f.withDomain(span, "netnew", null, null);

        assertEquals("netnew", span.attributes().get(BusinessSpanFactory.ATTR_SCENARIO));
        assertFalse(span.attributes().containsKey(BusinessSpanFactory.ATTR_CHANNEL));
    }

    @Test
    void duration_computed_from_injected_clock() {
        // Clock returns 0 at start, 100 at end -> duration 100.
        BusinessSpanFactory f = deterministicFactory();
        Span span = f.startTrace("op");
        assertEquals(0L, span.duration());
        assertFalse(span.isEnded());

        f.end(span);
        assertTrue(span.isEnded());
        assertEquals(100L, span.duration());
    }

    @Test
    void recorder_returns_spans_for_trace() {
        BusinessSpanFactory f = deterministicFactory();
        TraceRecorder recorder = new TraceRecorder();
        Span root = f.startTrace("checkout");
        Span child = f.startChild(root, "tax");
        recorder.record(root);
        recorder.record(child);

        assertEquals(2, recorder.spansForTrace(root.traceId()).size());
        assertTrue(recorder.spansForTrace("nope").isEmpty());
    }

    @Test
    void assemble_orders_parent_before_child_even_when_recorded_out_of_order() {
        BusinessSpanFactory f = deterministicFactory();
        TraceRecorder recorder = new TraceRecorder();
        Span root = f.startTrace("checkout");
        Span child = f.startChild(root, "tax");
        Span grandchild = f.startChild(child, "rate-call");

        // Record children before their parents.
        recorder.record(grandchild);
        recorder.record(child);
        recorder.record(root);

        List<Span> ordered = recorder.assemble(root.traceId());
        assertEquals(3, ordered.size());
        assertEquals(root.spanId(), ordered.get(0).spanId());
        assertEquals(child.spanId(), ordered.get(1).spanId());
        assertEquals(grandchild.spanId(), ordered.get(2).spanId());
    }

    @Test
    void assemble_unknown_trace_is_empty() {
        assertTrue(new TraceRecorder().assemble("missing").isEmpty());
    }

    @Test
    void recorder_counts_by_attribute() {
        BusinessSpanFactory f = deterministicFactory();
        TraceRecorder recorder = new TraceRecorder();
        Span root = f.startTrace("checkout");
        Span child = f.startChild(root, "tax");
        f.withDomain(root, "renewal", "01", "TR");
        f.withDomain(child, "renewal", "01", "TR");
        recorder.record(root);
        recorder.record(child);

        assertEquals(2L, recorder.countByAttribute(
                root.traceId(), BusinessSpanFactory.ATTR_CHANNEL, "01"));
        assertEquals(0L, recorder.countByAttribute(
                root.traceId(), BusinessSpanFactory.ATTR_CHANNEL, "03"));
    }

    @Test
    void error_catalog_counts_occurrences() {
        ErrorCatalog catalog = new ErrorCatalog();
        catalog.register("VAL_0046", "Missing configuration");
        assertEquals(1L, catalog.record("VAL_0046"));
        assertEquals(2L, catalog.record("VAL_0046"));
        assertEquals(2L, catalog.count("VAL_0046"));
        assertEquals("Missing configuration", catalog.description("VAL_0046"));
    }

    @Test
    void error_catalog_top_codes_ordered_by_frequency() {
        ErrorCatalog catalog = new ErrorCatalog();
        catalog.register("VAL_0046", "Missing configuration");
        catalog.register("VAL_0100", "Invalid price");
        catalog.record("VAL_0046");
        catalog.record("VAL_0046");
        catalog.record("VAL_0046");
        catalog.record("VAL_0100");

        List<ErrorCatalog.Entry> top = catalog.topCodes(2);
        assertEquals(2, top.size());
        assertEquals("VAL_0046", top.get(0).code());
        assertEquals(3L, top.get(0).count());
        assertEquals("VAL_0100", top.get(1).code());
        assertEquals("Invalid price", top.get(1).description());
    }

    @Test
    void error_catalog_handles_unregistered_and_limit() {
        ErrorCatalog catalog = new ErrorCatalog();
        catalog.record("VAL_9999");
        assertEquals("(unregistered)", catalog.description("VAL_9999"));
        assertTrue(catalog.topCodes(0).isEmpty());
        assertEquals(1, catalog.topCodes(5).size());
    }
}
