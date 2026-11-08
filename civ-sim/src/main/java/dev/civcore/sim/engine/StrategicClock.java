package dev.civcore.sim.engine;

/**
 * Turns real time into strategic ticks ("turns"). Driven by the server tick; never reads the wall clock itself, so it
 * is deterministic under test.
 */
public final class StrategicClock {
    /** Never run more than this many strategic ticks in one call (prevents a spiral after a long server stall). */
    public static final int MAX_CATCH_UP = 3;

    private long periodMillis;
    private long accumulatedMillis;
    private double speed = 1.0;
    private boolean paused;

    public StrategicClock(long periodMillis) {
        setPeriodMillis(periodMillis);
    }

    public void setPeriodMillis(long periodMillis) {
        if (periodMillis <= 0) throw new IllegalArgumentException("period must be positive");
        this.periodMillis = periodMillis;
    }

    public void setSpeed(double speed) {
        if (speed <= 0) throw new IllegalArgumentException("speed must be positive");
        this.speed = speed;
    }

    public double speed() {
        return speed;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean paused() {
        return paused;
    }

    /** Milliseconds of game time until the next strategic tick. */
    public long millisUntilNext() {
        return Math.max(0, periodMillis - accumulatedMillis);
    }

    /** Adds elapsed real time; returns how many strategic ticks are now due (0..MAX_CATCH_UP). */
    public int advance(long realMillis) {
        if (paused || realMillis <= 0) return 0;
        accumulatedMillis += Math.round(realMillis * speed);
        int due = (int) Math.min(MAX_CATCH_UP, accumulatedMillis / periodMillis);
        accumulatedMillis -= (long) due * periodMillis;
        if (accumulatedMillis >= periodMillis) accumulatedMillis = periodMillis - 1; // drop the excess beyond catch-up
        return due;
    }
}
