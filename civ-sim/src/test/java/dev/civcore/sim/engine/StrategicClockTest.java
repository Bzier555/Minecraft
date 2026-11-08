package dev.civcore.sim.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategicClockTest {

    @Test
    void oneHourAtSixtySecondsIsSixtyTicks() {
        StrategicClock c = new StrategicClock(60_000);
        int ticks = 0;
        for (int i = 0; i < 72_000; i++) ticks += c.advance(50); // 20 server ticks per second for one hour
        assertEquals(60, ticks);
    }

    @Test
    void pauseStopsTimeAndSpeedScalesIt() {
        StrategicClock c = new StrategicClock(1_000);
        c.setPaused(true);
        assertEquals(0, c.advance(10_000));
        c.setPaused(false);
        c.setSpeed(2.0);
        assertEquals(1, c.advance(500));
    }

    @Test
    void catchUpIsCapped() {
        StrategicClock c = new StrategicClock(1_000);
        assertEquals(StrategicClock.MAX_CATCH_UP, c.advance(60_000));
        assertTrue(c.millisUntilNext() > 0);
    }
}
