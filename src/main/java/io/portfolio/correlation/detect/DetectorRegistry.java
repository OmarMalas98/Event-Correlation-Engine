package io.portfolio.correlation.detect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The live set of detector definitions.
 *
 * <p>Detectors are data, not code, and they change while the engine is running — someone silences a
 * noisy rule at 3am, someone adds a new one during an incident. Every mutation here notifies a
 * listener so the subscription layer can react; that is the hook that makes activation take effect
 * immediately instead of at the next restart.
 */
@Component
public class DetectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(DetectorRegistry.class);

    private final ConcurrentMap<String, Detector> detectors = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void onChange(Runnable listener) {
        changeListeners.add(listener);
    }

    public Detector save(Detector detector) {
        detectors.put(detector.id(), detector);
        log.info("Registered detector '{}' on topic '{}' (active={})",
                detector.name(), detector.topic(), detector.active());
        notifyChanged();
        return detector;
    }

    public Optional<Detector> setActive(String id, boolean active) {
        Detector updated = detectors.computeIfPresent(id, (key, detector) -> detector.activated(active));
        if (updated != null) {
            log.info("Detector '{}' is now {}", updated.name(), active ? "active" : "inactive");
            notifyChanged();
        }
        return Optional.ofNullable(updated);
    }

    public Optional<Detector> find(String id) {
        return Optional.ofNullable(detectors.get(id));
    }

    public List<Detector> all() {
        return detectors.values().stream()
                .sorted(java.util.Comparator.comparing(Detector::name))
                .toList();
    }

    public List<EventDetector> activeEventDetectorsFor(String topic) {
        return detectors.values().stream()
                .filter(Detector::active)
                .filter(EventDetector.class::isInstance)
                .map(EventDetector.class::cast)
                .filter(detector -> detector.topic().equals(topic))
                .toList();
    }

    public List<WindowDetector> activeWindowDetectors() {
        return detectors.values().stream()
                .filter(Detector::active)
                .filter(WindowDetector.class::isInstance)
                .map(WindowDetector.class::cast)
                .toList();
    }

    /** Topics that at least one active detector needs consumed. */
    public java.util.Set<String> requiredTopics() {
        return detectors.values().stream()
                .filter(Detector::active)
                .map(Detector::topic)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void notifyChanged() {
        changeListeners.forEach(Runnable::run);
    }
}
