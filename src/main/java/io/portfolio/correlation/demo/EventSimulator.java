package io.portfolio.correlation.demo;

import io.portfolio.correlation.stream.Event;
import io.portfolio.correlation.stream.EventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates synthetic traffic so the engine has something to correlate.
 *
 * <p>Not a load generator — a scenario generator. The interesting question is not "can it handle
 * volume" but "does a burst of related failures become one case rather than forty alerts", and that
 * needs events with deliberate structure.
 */
@Component
public class EventSimulator {

    private static final Logger log = LoggerFactory.getLogger(EventSimulator.class);

    private static final List<String> REGIONS = List.of("eu-west", "us-east", "ap-south");
    private static final List<String> SERVICES = List.of("checkout", "refunds", "payouts");

    private final EventStream stream;

    public EventSimulator(EventStream stream) {
        this.stream = stream;
    }

    /**
     * Ordinary traffic: fast, successful, uninteresting. Publishing this matters — a failure rate
     * needs a denominator, and without healthy events every window looks like a total outage.
     */
    public int publishHealthy(int count) {
        for (int i = 0; i < count; i++) {
            publish(pick(REGIONS), pick(SERVICES), randomBetween(80, 400), false);
        }
        log.info("Published {} healthy events", count);
        return count;
    }

    /**
     * A localised incident: one region and service, mostly failing, mostly slow. This is what
     * should collapse into a single case per grouping.
     */
    public int publishIncident(String region, String service, int count) {
        for (int i = 0; i < count; i++) {
            boolean failed = ThreadLocalRandom.current().nextInt(100) < 70;
            long duration = failed ? randomBetween(3200, 9000) : randomBetween(200, 900);
            publish(region, service, duration, failed);
        }
        log.info("Published {} incident events for {}/{}", count, region, service);
        return count;
    }

    private void publish(String region, String service, long durationMs, boolean failed) {
        stream.publish(Event.of(
                DemoSeeder.TOPIC,
                region + "/" + service,
                Instant.now(),
                Map.of(
                        "region", region,
                        "service", service,
                        "duration_ms", durationMs,
                        // 0/1 rather than a boolean so PERCENTAGE can read it as a number: the
                        // aggregate is "share of events where this field is non-zero".
                        "failed", failed ? 1 : 0,
                        "amount", randomBetween(5, 5000))));
    }

    private String pick(List<String> options) {
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    private long randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }
}
