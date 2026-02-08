package org.lucee.lucli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Timer class.
 * Tests timing measurement, reporting, and lifecycle.
 */
class TimerTest {

    @BeforeEach
    void setUp() {
        // Ensure clean state before each test
        Timer.clear();
        Timer.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        Timer.clear();
        Timer.setEnabled(false);
    }

    // ============================================
    // Singleton Tests
    // ============================================

    @Test
    void testGetInstance() {
        Timer instance1 = Timer.getInstance();
        Timer instance2 = Timer.getInstance();
        
        assertNotNull(instance1);
        assertSame(instance1, instance2, "getInstance should return the same instance");
    }

    // ============================================
    // Enable/Disable Tests
    // ============================================

    @Test
    void testSetEnabled() {
        Timer.setEnabled(true);
        assertTrue(Timer.isEnabled());
        
        Timer.setEnabled(false);
        assertFalse(Timer.isEnabled());
    }

    @Test
    void testDisableClears() {
        Timer.setEnabled(true);
        Timer.start("test");
        Timer.stop("test");
        
        Timer.setEnabled(false);
        
        // After disabling, getDuration should return ZERO
        assertEquals(Duration.ZERO, Timer.getDuration("test"));
    }

    @Test
    void testTimerDoesNothingWhenDisabled() {
        Timer.setEnabled(false);
        
        Timer.start("disabled-test");
        Timer.stop("disabled-test");
        
        assertEquals(Duration.ZERO, Timer.getDuration("disabled-test"));
    }

    // ============================================
    // Start/Stop Tests
    // ============================================

    @Test
    void testStartStop() {
        Timer.start("operation");
        
        // Simulate some work
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        Duration duration = Timer.stop("operation");
        
        assertNotNull(duration);
        assertTrue(duration.toMillis() >= 10, "Duration should be at least 10ms");
    }

    @Test
    void testStopWithoutStart() {
        Duration duration = Timer.stop("never-started");
        assertEquals(Duration.ZERO, duration);
    }

    @Test
    void testMultipleOperations() {
        Timer.start("op1");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        Timer.stop("op1");
        
        Timer.start("op2");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        Timer.stop("op2");
        
        Duration d1 = Timer.getDuration("op1");
        Duration d2 = Timer.getDuration("op2");
        
        assertTrue(d1.toMillis() >= 10);
        assertTrue(d2.toMillis() >= 10);
    }

    @Test
    void testOverlappingOperations() {
        Timer.start("outer");
        Timer.start("inner");
        
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        Duration innerDuration = Timer.stop("inner");
        
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        Duration outerDuration = Timer.stop("outer");
        
        assertTrue(innerDuration.toMillis() >= 10);
        assertTrue(outerDuration.toMillis() >= 20);
        assertTrue(outerDuration.compareTo(innerDuration) > 0);
    }

    // ============================================
    // GetDuration Tests
    // ============================================

    @Test
    void testGetDurationAfterStop() {
        Timer.start("test");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        Timer.stop("test");
        
        Duration duration = Timer.getDuration("test");
        assertTrue(duration.toMillis() >= 10);
    }

    @Test
    void testGetDurationNonExistent() {
        Duration duration = Timer.getDuration("nonexistent");
        assertEquals(Duration.ZERO, duration);
    }

    // ============================================
    // Clear Tests
    // ============================================

    @Test
    void testClear() {
        Timer.start("clearable");
        Timer.stop("clearable");
        
        assertNotEquals(Duration.ZERO, Timer.getDuration("clearable"));
        
        Timer.clear();
        
        assertEquals(Duration.ZERO, Timer.getDuration("clearable"));
    }

    // ============================================
    // Instance Method Tests
    // ============================================

    @Test
    void testInstanceStartStop() {
        Timer timer = Timer.getInstance();
        
        timer._start("instance-test");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        Duration duration = timer._stop("instance-test");
        
        assertTrue(duration.toMillis() >= 10);
    }

    @Test
    void testInstanceSetEnabled() {
        Timer timer = Timer.getInstance();
        
        timer._setEnabled(true);
        assertTrue(timer._isEnabled());
        
        timer._setEnabled(false);
        assertFalse(timer._isEnabled());
    }

    @Test
    void testInstanceGetDuration() {
        Timer timer = Timer.getInstance();
        timer._setEnabled(true);
        
        timer._start("inst-dur");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        timer._stop("inst-dur");
        
        Duration duration = timer._getDuration("inst-dur");
        assertTrue(duration.toMillis() >= 10);
    }

    @Test
    void testInstanceClear() {
        Timer timer = Timer.getInstance();
        timer._setEnabled(true);
        
        timer._start("inst-clear");
        timer._stop("inst-clear");
        
        timer._clear();
        
        assertEquals(Duration.ZERO, timer._getDuration("inst-clear"));
    }

    // ============================================
    // PrintResults Tests (No Exception)
    // ============================================

    @Test
    void testPrintResultsNoException() {
        Timer.start("print-test");
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        Timer.stop("print-test");
        
        // Should not throw
        assertDoesNotThrow(() -> Timer.printResults());
    }

    @Test
    void testPrintResultsEmpty() {
        Timer.clear();
        // Should not throw even with no timers
        assertDoesNotThrow(() -> Timer.printResults());
    }

    @Test
    void testPrintResultsDisabled() {
        Timer.setEnabled(false);
        // Should not throw when disabled
        assertDoesNotThrow(() -> Timer.printResults());
    }

    @Test
    void testInstancePrintResultsBar() {
        Timer timer = Timer.getInstance();
        timer._setEnabled(true);
        
        timer._start("bar-test");
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        timer._stop("bar-test");
        
        assertDoesNotThrow(() -> timer._printResultsBar());
    }

    @Test
    void testInstancePrintResults() {
        Timer timer = Timer.getInstance();
        timer._setEnabled(true);
        
        timer._start("results-test");
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        timer._stop("results-test");
        
        assertDoesNotThrow(() -> timer._printResults());
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    void testSameOperationNameTwice() {
        Timer.start("duplicate");
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        Timer.stop("duplicate");
        Duration first = Timer.getDuration("duplicate");
        
        // Start again with same name
        Timer.start("duplicate");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        Timer.stop("duplicate");
        Duration second = Timer.getDuration("duplicate");
        
        // Second measurement should overwrite first
        assertTrue(second.toMillis() >= 10);
    }

    @Test
    void testSpecialCharactersInName() {
        Timer.start("test → operation");
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        Timer.stop("test → operation");
        
        Duration duration = Timer.getDuration("test → operation");
        assertTrue(duration.toMillis() >= 5);
    }

    @Test
    void testVeryLongOperationName() {
        String longName = "a".repeat(200);
        
        Timer.start(longName);
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        Timer.stop(longName);
        
        Duration duration = Timer.getDuration(longName);
        assertTrue(duration.toMillis() >= 5);
    }

    @Test
    void testEmptyOperationName() {
        Timer.start("");
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        Timer.stop("");
        
        Duration duration = Timer.getDuration("");
        assertTrue(duration.toMillis() >= 5);
    }

    // ============================================
    // Concurrent Operations Tests
    // ============================================

    @Test
    void testNestedTimers() {
        Timer.start("level1");
        Timer.start("level1-level2");
        Timer.start("level1-level2-level3");
        
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        
        Timer.stop("level1-level2-level3");
        Timer.stop("level1-level2");
        Timer.stop("level1");
        
        Duration d1 = Timer.getDuration("level1");
        Duration d2 = Timer.getDuration("level1-level2");
        Duration d3 = Timer.getDuration("level1-level2-level3");
        
        // All should have recorded durations
        assertTrue(d1.toMillis() >= 5);
        assertTrue(d2.toMillis() >= 5);
        assertTrue(d3.toMillis() >= 5);
    }

    // ============================================
    // Duration Accuracy Tests
    // ============================================

    @Test
    void testMillisecondAccuracy() {
        Timer.start("accuracy");
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        Timer.stop("accuracy");
        
        Duration duration = Timer.getDuration("accuracy");
        
        // Should be within a reasonable range (100ms ± 50ms)
        assertTrue(duration.toMillis() >= 100, "Duration should be at least 100ms");
        assertTrue(duration.toMillis() < 200, "Duration should be less than 200ms");
    }

    @Test
    void testZeroDurationPossible() {
        Timer.start("fast");
        Timer.stop("fast");
        
        Duration duration = Timer.getDuration("fast");
        // Very fast operations may have 0ms duration
        assertTrue(duration.toMillis() >= 0);
    }

    // ============================================
    // Static vs Instance Consistency
    // ============================================

    @Test
    void testStaticAndInstanceConsistency() {
        Timer timer = Timer.getInstance();
        
        Timer.start("consistency");
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        // Stop via instance
        timer._stop("consistency");
        
        // Get duration via static method
        Duration duration = Timer.getDuration("consistency");
        
        assertTrue(duration.toMillis() >= 10);
    }
}
