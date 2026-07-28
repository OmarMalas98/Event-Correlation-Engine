package io.portfolio.correlation.detect;

import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.alert.AlertPublisher;
import io.portfolio.correlation.stream.Event;
import io.portfolio.correlation.stream.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Evaluates window detectors on a schedule.
 *
 * <p>For each active detector: take the events in its window, group them by its dimensions, reduce
 * each group with its aggregation function, and raise an alert for every group that breaches the
 * threshold. Grouping is what makes the output actionable — "error rate above 5% in region eu-west
 * on checkout" rather than "error rate above 5% somewhere".
 */
@Component
public class WindowEvaluator {

    private static final Logger log = LoggerFactory.getLogger(WindowEvaluator.class);

    private final DetectorRegistry registry;
    private final EventStore store;
    private final AlertPublisher alerts;

    public WindowEvaluator(DetectorRegistry registry, EventStore store, AlertPublisher alerts) {
        this.registry = registry;
        this.store = store;
        this.alerts = alerts;
    }

    @Scheduled(fixedDelayString = "${correlation.window-evaluation-interval:PT10S}")
    public void evaluateAll() {
        registry.activeWindowDetectors().forEach(detector -> {
            try {
                evaluate(detector, Instant.now());
            } catch (RuntimeException e) {
                log.error("Window detector '{}' failed to evaluate", detector.name(), e);
            }
        });
    }

    /**
     * Evaluates one detector as at {@code now}, returning the alerts it raised.
     *
     * <p>Taking {@code now} as a parameter rather than reading the clock is what makes this
     * testable: a window detector is a statement about a time range, and a test that cannot choose
     * the range cannot check the boundaries.
     */
    public List<Alert> evaluate(WindowDetector detector, Instant now) {
        Instant from = now.minus(detector.window());
        List<Event> window = store.between(detector.topic(), from, now);
        if (window.isEmpty()) {
            return List.of();
        }

        Map<Map<String, Object>, List<Event>> groups = group(window, detector.dimensions());

        List<Alert> raised = groups.entrySet().stream()
                .map(entry -> assess(detector, entry.getKey(), entry.getValue(), from, now))
                .filter(java.util.Objects::nonNull)
                .toList();

        raised.forEach(alerts::publish);
        return raised;
    }

    private Alert assess(WindowDetector detector, Map<String, Object> dimensions,
                         List<Event> events, Instant from, Instant to) {
        double actual = detector.function().apply(events, detector.factField());
        if (!detector.operator().breached(actual, detector.threshold())) {
            return null;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("function", detector.function().name());
        details.put("factField", detector.factField());
        details.put("observed", round(actual));
        details.put("threshold", detector.threshold());
        details.put("comparison", detector.operator().symbol());
        details.put("sampleSize", events.size());
        details.put("windowFrom", from.toString());
        details.put("windowTo", to.toString());

        log.info("Window detector '{}' breached for {}: {} {} {} {} (n={})",
                detector.name(), dimensions.isEmpty() ? "the whole window" : dimensions,
                detector.function(), round(actual), detector.operator().symbol(),
                detector.threshold(), events.size());

        String source = dimensions.isEmpty()
                ? detector.topic()
                : dimensions.values().stream().map(String::valueOf).collect(Collectors.joining("/"));

        return Alert.raise(detector.id(), detector.name(), detector.topic(),
                detector.severity(), source, dimensions, details);
    }

    /**
     * Groups events by the values of the detector's dimension attributes. No dimensions means one
     * group covering the whole window.
     */
    private Map<Map<String, Object>, List<Event>> group(List<Event> events, List<String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return Map.of(Map.of(), events);
        }
        return events.stream().collect(Collectors.groupingBy(
                event -> {
                    Map<String, Object> key = new LinkedHashMap<>();
                    dimensions.forEach(dimension -> key.put(dimension, event.text(dimension)));
                    return key;
                },
                LinkedHashMap::new,
                Collectors.toList()));
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
