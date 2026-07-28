package io.portfolio.correlation.stream;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One raw observation off the stream.
 *
 * <p>Attributes are an open map rather than typed fields because the engine is deliberately
 * schema-agnostic: it correlates whatever arrives, and the fields that matter are named in detector
 * definitions at runtime rather than in code at compile time. Pinning a schema here would mean a
 * release every time a new event source appeared.
 *
 * <p>The accessors below exist so detector expressions stay readable — {@code
 * #event.number('latency_ms') > 500} rather than a cast-and-null-check in every condition.
 */
public record Event(
        String id,
        String topic,
        String source,
        Instant occurredAt,
        Map<String, Object> attributes
) {

    public static Event of(String topic, String source, Instant occurredAt, Map<String, Object> attributes) {
        return new Event(UUID.randomUUID().toString(), topic, source, occurredAt, Map.copyOf(attributes));
    }

    /**
     * A numeric attribute, or {@code 0} when absent or non-numeric.
     *
     * <p>Returning zero rather than throwing is a considered choice: detector expressions are
     * configuration, they run against every event on a topic, and a single malformed event must not
     * be able to take a detector out of service.
     */
    public double number(String field) {
        Object value = attributes.get(field);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    /** A string attribute, or empty when absent. */
    public String text(String field) {
        Object value = attributes.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    public boolean has(String field) {
        return attributes.containsKey(field);
    }
}
