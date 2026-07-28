package io.portfolio.correlation.stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A bounded, per-topic ring of recent events.
 *
 * <p>Bounded is the whole point. An unbounded buffer in front of a high-volume stream is an
 * out-of-memory error with a delay on it, and the retention only has to outlast the longest
 * detector window — nothing older can affect a decision.
 */
@Component
public class InMemoryEventStore implements EventStore {

    private final ConcurrentMap<String, Deque<Event>> byTopic = new ConcurrentHashMap<>();
    private final int maxEventsPerTopic;

    public InMemoryEventStore(@Value("${correlation.store.max-events-per-topic:10000}") int maxEventsPerTopic) {
        this.maxEventsPerTopic = maxEventsPerTopic;
    }

    @Override
    public void record(Event event) {
        Deque<Event> events = byTopic.computeIfAbsent(event.topic(), key -> new ArrayDeque<>());
        synchronized (events) {
            events.addLast(event);
            while (events.size() > maxEventsPerTopic) {
                events.removeFirst();
            }
        }
    }

    @Override
    public List<Event> between(String topic, Instant from, Instant to) {
        Deque<Event> events = byTopic.get(topic);
        if (events == null) {
            return List.of();
        }
        List<Event> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        return snapshot.stream()
                .filter(event -> !event.occurredAt().isBefore(from) && event.occurredAt().isBefore(to))
                .toList();
    }

    @Override
    public int size(String topic) {
        Deque<Event> events = byTopic.get(topic);
        if (events == null) {
            return 0;
        }
        synchronized (events) {
            return events.size();
        }
    }

    public Set<String> topics() {
        return Set.copyOf(byTopic.keySet());
    }
}
