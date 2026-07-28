package io.portfolio.correlation.detect;

import io.portfolio.correlation.stream.Event;
import io.portfolio.correlation.stream.EventStream;
import io.portfolio.correlation.stream.Subscription;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Keeps the set of live stream subscriptions in step with the set of active detectors.
 *
 * <p>The engine consumes a topic if and only if some active detector needs it. That sounds obvious
 * and is easy to get wrong in exactly one way: <b>topics are shared</b>. Five detectors may sit on
 * {@code payments}, and deactivating one of them must not stop the other four from receiving
 * anything.
 *
 * <p>So this reconciles against the whole required set rather than reacting to individual
 * activations — compute which topics are needed now, open what is missing, close what is no longer
 * wanted. The reconcile is idempotent, which means it can be re-run at any time without having to
 * reason about which change triggered it, and there is no per-topic counter to drift out of sync.
 */
@Component
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    private final EventStream stream;
    private final DetectorRegistry registry;
    private final Consumer<Event> dispatcher;

    private final ConcurrentMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SubscriptionManager(EventStream stream, DetectorRegistry registry, EventDispatcher dispatcher) {
        this.stream = stream;
        this.registry = registry;
        this.dispatcher = dispatcher::dispatch;
    }

    @PostConstruct
    void start() {
        registry.onChange(this::reconcile);
        reconcile();
    }

    /**
     * Opens and closes subscriptions so that the consumed set equals the required set.
     */
    public synchronized void reconcile() {
        Set<String> required = registry.requiredTopics();
        Set<String> current = new HashSet<>(subscriptions.keySet());

        for (String topic : required) {
            if (!current.contains(topic)) {
                subscriptions.put(topic, stream.subscribe(topic, dispatcher));
                log.info("Started consuming '{}'", topic);
            }
        }

        for (String topic : current) {
            if (!required.contains(topic)) {
                Subscription subscription = subscriptions.remove(topic);
                if (subscription != null) {
                    subscription.close();
                    log.info("Stopped consuming '{}' — no active detector needs it", topic);
                }
            }
        }
    }

    public Set<String> consumedTopics() {
        return Set.copyOf(subscriptions.keySet());
    }

    @PreDestroy
    void stop() {
        subscriptions.values().forEach(Subscription::close);
        subscriptions.clear();
    }
}
