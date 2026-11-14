
package tn.esprit.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BasicUnitTest {

    @Test
    void testAddition() {
        int result = 10 + 20;

        assertEquals(30, result);
    }

    @Test
    void testStringNotNull() {
        String message = "Backend CI/CD";

        assertNotNull(message);
    }

    @Test
    void testBoolean() {
        assertTrue(10 > 5);
    }
}

