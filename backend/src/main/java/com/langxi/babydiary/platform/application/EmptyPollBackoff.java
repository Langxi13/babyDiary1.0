package com.langxi.babydiary.platform.application;

import java.time.Duration;

final class EmptyPollBackoff {
    private static final long[] DELAYS_SECONDS = {2, 4, 8, 15, 30};
    private int delayIndex;
    private long nextPollNanos;
    private long observedSignal = -1;

    synchronized boolean ready(long signalVersion) {
        if (signalVersion != observedSignal) {
            observedSignal = signalVersion;
            active();
        }
        return System.nanoTime() >= nextPollNanos;
    }

    synchronized void empty() {
        long delay = DELAYS_SECONDS[Math.min(delayIndex, DELAYS_SECONDS.length - 1)];
        delayIndex = Math.min(delayIndex + 1, DELAYS_SECONDS.length - 1);
        nextPollNanos = System.nanoTime() + Duration.ofSeconds(delay).toNanos();
    }

    synchronized void active() {
        delayIndex = 0;
        nextPollNanos = 0;
    }
}
