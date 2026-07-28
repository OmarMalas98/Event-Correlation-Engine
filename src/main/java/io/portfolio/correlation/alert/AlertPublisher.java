package io.portfolio.correlation.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Where alerts go once raised.
 *
 * <p>Detectors and correlation are decoupled through this rather than wired together directly.
 * That keeps detection ignorant of what happens to its output — an alert might be correlated,
 * forwarded to another system, or simply counted — and it is what lets the correlation stage be
 * tested against hand-built alerts with no stream involved.
 */
@Component
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

    private final List<Consumer<Alert>> subscribers = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, Alert> alerts = new ConcurrentHashMap<>();

    public void subscribe(Consumer<Alert> subscriber) {
        subscribers.add(subscriber);
    }

    public void publish(Alert alert) {
        alerts.put(alert.id(), alert);
        log.info("Alert '{}' [{}] from {} {}", alert.detectorName(), alert.severity(),
                alert.source(), alert.attributes());
        subscribers.forEach(subscriber -> subscriber.accept(alert));
    }

    /**
     * Marks an alert resolved. Cases only auto-close once every alert they hold is resolved, so
     * this is what eventually lets a case go quiet.
     */
    public Optional<Alert> resolve(String alertId) {
        Alert resolved = alerts.computeIfPresent(alertId, (id, alert) -> alert.resolved());
        if (resolved != null) {
            log.info("Alert '{}' resolved", resolved.detectorName());
        }
        return Optional.ofNullable(resolved);
    }

    public Optional<Alert> find(String alertId) {
        return Optional.ofNullable(alerts.get(alertId));
    }

    public List<Alert> all() {
        return new ArrayList<>(alerts.values());
    }

    public long activeCount() {
        return alerts.values().stream().filter(alert -> !alert.isResolved()).count();
    }
}
