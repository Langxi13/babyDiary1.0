package com.langxi.babydiary.platform.application;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class WorkerPollSignal {
    private final AtomicLong jobs = new AtomicLong();
    private final AtomicLong outbox = new AtomicLong();

    public long jobsVersion() {
        return jobs.get();
    }

    public long outboxVersion() {
        return outbox.get();
    }

    public void jobEnqueued() {
        jobs.incrementAndGet();
    }

    public void outboxEnqueued() {
        outbox.incrementAndGet();
    }
}
