package io.portfolio.correlation.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * An in-process stand-in for the message bus.
 *
 * <p>Publishing is synchronous, which is a simplification with one deliberate benefit: it makes the
 * demo and the tests fully deterministic. Publish an event, and by the time the call returns every
 * detector has seen it and any resulting case exists. Asserting on that under a real broker means
 * polling and hoping.
 *
 * <p>{@link CopyOnWriteArrayList} for the listener lists is the right shape here — subscriptions
 * change rarely (only when a detector is activated) and are read on every single event.
 */
@Component
public class InMemoryEventStream implements EventStream {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventStream.class);

    private final ConcurrentMap<String, List<Consumer<Event>>> listeners = new ConcurrentHashMap<>();

    @Override
    public Subscription subscribe(String topic, Consumer<Event> listener) {
        listeners.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("Subscribed to '{}'", topic);

        return new Subscription() {
            @Override
            public String topic() {
                return topic;
            }

            @Override
            public void close() {
                // Drop the topic entirely once nothing is listening, so subscribedTopics() reports
                // what is actually being consumed rather than what once was. computeIfPresent keeps
                // the remove-and-prune atomic against a concurrent subscribe.
                listeners.computeIfPresent(topic, (key, registered) -> {
                    registered.remove(listener);
                    return registered.isEmpty() ? null : registered;
                });
                log.debug("Unsubscribed from '{}'", topic);
            }
        };
    }

    @Override
    public void publish(Event event) {
        List<Consumer<Event>> registered = listeners.get(event.topic());
        if (registered == null || registered.isEmpty()) {
            return;
        }
        for (Consumer<Event> listener : registered) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // One misbehaving detector must not stop the others from seeing this event, and
                // must not stop the stream. A real broker would also need this, or a single bad
                // expression takes down consumption for everything sharing the topic.
                log.error("A listener on '{}' failed for event {}", event.topic(), event.id(), e);
            }
        }
    }

    @Override
    public Set<String> subscribedTopics() {
        return Set.copyOf(listeners.keySet());
    }
}
