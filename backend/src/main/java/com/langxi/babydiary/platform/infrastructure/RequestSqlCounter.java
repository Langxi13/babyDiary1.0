package com.langxi.babydiary.platform.infrastructure;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RequestSqlCounter {
    private final ThreadLocal<Counter> current = new ThreadLocal<>();
    private final DistributionSummary requests;

    public RequestSqlCounter(MeterRegistry metrics) {
        this.requests =
                DistributionSummary.builder("baby.diary.http.sql.count")
                        .description("SQL statements executed while handling one HTTP request")
                        .register(metrics);
    }

    public void begin() {
        current.set(new Counter());
    }

    public void increment() {
        Counter counter = current.get();
        if (counter != null) counter.value++;
    }

    public int end() {
        Counter counter = current.get();
        current.remove();
        int value = counter == null ? 0 : counter.value;
        requests.record(value);
        return value;
    }

    private static final class Counter {
        private int value;
    }
}
