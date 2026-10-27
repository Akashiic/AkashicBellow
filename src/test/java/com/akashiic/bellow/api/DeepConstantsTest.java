package com.akashiic.bellow.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeepConstantsTest {

    @Test
    void slots() {
        assertEquals(31, DeepConstants.slot(-1));
        assertEquals(0, DeepConstants.slot(-32));
        assertEquals(-1, DeepConstants.slot(-33));
        assertEquals(-1, DeepConstants.slot(0));
        assertEquals(31, DeepConstants.slot(-1 >> 4)); // y = -1
        assertEquals(28, DeepConstants.slot(-64 >> 4)); // y = -64
    }

    @Test
    void sanitize() {
        assertEquals(-64, DeepConstants.sanitizeMinY(-64));
        assertEquals(-80, DeepConstants.sanitizeMinY(-70));
        assertEquals(-512, DeepConstants.sanitizeMinY(-9000));
        assertEquals(0, DeepConstants.sanitizeMinY(5));
        assertEquals(-16, DeepConstants.sanitizeMinY(-1));
    }
}
