package com.sapcommercetools.otel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusinessSpanFactoryTest {

    private final BusinessSpanFactory subject = new BusinessSpanFactory();

    @Test
    void describes_itself() {
        assertTrue(subject.describe().startsWith("commerce-otel"));
    }

    @Test
    void accepts_non_blank_input() {
        assertTrue(subject.accepts("cart-123"));
        assertFalse(subject.accepts(" "));
        assertFalse(subject.accepts(null));
    }
}
