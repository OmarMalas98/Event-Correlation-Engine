package io.portfolio.correlation.stream;

import java.time.Instant;
import java.util.List;

/**
 * Retained events, queryable by time.
 *
 * <p>The second port. Window detectors cannot work off the live stream alone — "the average over
 * the last five minutes" is a question about the past, not about the event in hand. In production
 * this is a search index; here it is a bounded in-memory ring.
 */
public interface EventStore {

    void record(Event event);

    /**
     * Events on a topic within {@code [from, to)}, oldest first.
     */
    List<Event> between(String topic, Instant from, Instant to);

    int size(String topic);
}
