package io.portfolio.correlation.detect;

/**
 * Fires when a single event satisfies a condition.
 *
 * <p>The condition is a Spring Expression Language string evaluated against the event, for example
 * {@code #event.number('latency_ms') > 2000} or
 * {@code #event.text('status') == 'FAILED' and #event.number('amount') > 10000}.
 *
 * @param carryFields event attributes copied onto the alert, so downstream correlation can group by
 *                    them without going back to the original event
 */
public record EventDetector(
        String id,
        String name,
        String topic,
        String condition,
        String severity,
        java.util.List<String> carryFields,
        boolean active
) implements Detector {

    @Override
    public Detector activated(boolean active) {
        return new EventDetector(id, name, topic, condition, severity, carryFields, active);
    }
}
