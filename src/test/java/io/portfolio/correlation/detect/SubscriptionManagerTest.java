package io.portfolio.correlation.detect;

import io.portfolio.correlation.stream.InMemoryEventStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Subscriptions must track the active detector set exactly — and the case that is easy to get wrong
 * is a topic several detectors share.
 */
@SpringBootTest
class SubscriptionManagerTest {

    @Autowired
    private DetectorRegistry detectors;

    @Autowired
    private SubscriptionManager subscriptions;

    @Autowired
    private InMemoryEventStream stream;

    @Test
    @DisplayName("activating a detector on a new topic starts consuming it")
    void startsConsumingOnActivation() {
        detectors.save(detector("orders-detector", "orders", true));

        assertThat(subscriptions.consumedTopics()).contains("orders");
        assertThat(stream.subscribedTopics()).contains("orders");
    }

    @Test
    @DisplayName("a topic no active detector needs is not consumed")
    void doesNotConsumeUnneededTopics() {
        detectors.save(detector("shipping-detector", "shipping", false));

        assertThat(subscriptions.consumedTopics()).doesNotContain("shipping");
    }

    @Test
    @DisplayName("deactivating the last detector on a topic stops consuming it")
    void stopsConsumingWhenLastDetectorGoes() {
        detectors.save(detector("returns-detector", "returns", true));
        assertThat(subscriptions.consumedTopics()).contains("returns");

        detectors.setActive("returns-detector", false);

        assertThat(subscriptions.consumedTopics()).doesNotContain("returns");
        assertThat(stream.subscribedTopics()).doesNotContain("returns");
    }

    @Test
    @DisplayName("deactivating one detector on a shared topic must not stop the others receiving events")
    void sharedTopicSurvivesOneDetectorLeaving() {
        detectors.save(detector("shared-a", "invoices", true));
        detectors.save(detector("shared-b", "invoices", true));

        detectors.setActive("shared-a", false);

        assertThat(subscriptions.consumedTopics())
                .as("shared-b still needs this topic; unsubscribing would silently blind it")
                .contains("invoices");

        detectors.setActive("shared-b", false);

        assertThat(subscriptions.consumedTopics())
                .as("with nothing left needing it, the topic should be released")
                .doesNotContain("invoices");
    }

    @Test
    @DisplayName("reconciling repeatedly changes nothing")
    void reconcileIsIdempotent() {
        detectors.save(detector("idempotent-detector", "audit", true));
        var afterFirst = subscriptions.consumedTopics();

        subscriptions.reconcile();
        subscriptions.reconcile();

        assertThat(subscriptions.consumedTopics()).isEqualTo(afterFirst);
    }

    private EventDetector detector(String id, String topic, boolean active) {
        return new EventDetector(id, id, topic, "true", "LOW", List.of(), active);
    }
}
