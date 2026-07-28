package io.portfolio.correlation.alert;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A detector's finding: something worth a human's attention, probably.
 *
 * <p>Alerts are the intermediate currency of the engine. They are more meaningful than events and
 * far fewer, but there are still too many of them to hand to a person one at a time — which is what
 * the correlation stage is for.
 *
 * @param attributes the fields carried over from the triggering event or window group. These are
 *                   what case classifiers group by, so they are the difference between "twelve
 *                   alerts" and "one problem in one region"
 */
public record Alert(
        String id,
        String detectorId,
        String detectorName,
        String topic,
        String severity,
        String source,
        Instant raisedAt,
        AlertStatus status,
        Map<String, Object> attributes,
        Map<String, Object> details
) {

    public static Alert raise(String detectorId, String detectorName, String topic, String severity,
                              String source, Map<String, Object> attributes, Map<String, Object> details) {
        return new Alert(
                UUID.randomUUID().toString(),
                detectorId,
                detectorName,
                topic,
                severity,
                source,
                Instant.now(),
                AlertStatus.ACTIVE,
                new LinkedHashMap<>(attributes),
                new LinkedHashMap<>(details));
    }

    public Alert resolved() {
        return new Alert(id, detectorId, detectorName, topic, severity, source,
                raisedAt, AlertStatus.RESOLVED, attributes, details);
    }

    /**
     * An attribute as text, for use in case-classifier expressions. Empty rather than null so an
     * expression like {@code #alert.attribute('region')} never yields a null classifier.
     */
    public String attribute(String field) {
        Object value = attributes.get(field);
        return value == null ? "" : String.valueOf(value);
    }

    public boolean isResolved() {
        return status == AlertStatus.RESOLVED;
    }
}
