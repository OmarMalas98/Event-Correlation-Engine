package io.portfolio.correlation.stream;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The engine's view of the message bus.
 *
 * <p>A port, and the most important one in the project. In production this is Kafka; here it is an
 * in-memory implementation, so the engine runs with nothing installed. What matters is the shape:
 * <b>subscriptions can be opened and closed at runtime</b>. That is what makes detectors
 * activatable without a restart, and it is the requirement that a naive {@code @KafkaListener}
 * annotation cannot satisfy — annotations are fixed at startup.
 */
public interface EventStream {

    /**
     * Starts delivering events on {@code topic} to {@code listener}.
     *
     * @return a handle that stops delivery when closed
     */
    Subscription subscribe(String topic, Consumer<Event> listener);

    void publish(Event event);

    /** Topics with at least one live subscription. */
    Set<String> subscribedTopics();
}
