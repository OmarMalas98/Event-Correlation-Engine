package io.portfolio.correlation.detect;

import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.stream.Event;
import io.portfolio.correlation.stream.EventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Window detection: the aggregate, the grouping, and the window boundary.
 */
@SpringBootTest
class WindowEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    @Autowired
    private WindowEvaluator evaluator;

    @Autowired
    private EventStore store;

    @Test
    @DisplayName("a breaching group raises an alert; a healthy one does not")
    void raisesOnlyForBreachingGroups() {
        String topic = uniqueTopic();
        // eu-west: 4 of 5 failed (80%). us-east: 0 of 5 (0%).
        record(topic, "eu-west", true, 4);
        record(topic, "eu-west", false, 1);
        record(topic, "us-east", false, 5);

        List<Alert> raised = evaluator.evaluate(failureRateOver(topic, 20d), NOW);

        assertThat(raised).hasSize(1);
        Alert alert = raised.get(0);
        assertThat(alert.attribute("region")).isEqualTo("eu-west");
        assertThat((double) alert.details().get("observed")).isEqualTo(80d);
        assertThat(alert.details().get("sampleSize")).isEqualTo(5);
    }

    @Test
    @DisplayName("grouping is what makes the alert actionable")
    void groupsByDimension() {
        String topic = uniqueTopic();
        record(topic, "eu-west", true, 5);
        record(topic, "us-east", true, 5);

        List<Alert> raised = evaluator.evaluate(failureRateOver(topic, 20d), NOW);

        assertThat(raised)
                .as("two regions failing is two problems, not one")
                .hasSize(2);
        assertThat(raised.stream().map(alert -> alert.attribute("region")))
                .containsExactlyInAnyOrder("eu-west", "us-east");
    }

    @Test
    @DisplayName("a threshold that is not crossed raises nothing")
    void silentBelowThreshold() {
        String topic = uniqueTopic();
        record(topic, "eu-west", true, 1);
        record(topic, "eu-west", false, 9);

        assertThat(evaluator.evaluate(failureRateOver(topic, 20d), NOW))
                .as("10% is under a 20% threshold")
                .isEmpty();
    }

    @Test
    @DisplayName("events outside the window are not counted")
    void respectsTheWindowBoundary() {
        String topic = uniqueTopic();
        // Old enough to fall outside a 5-minute window.
        recordAt(topic, "eu-west", true, 10, NOW.minus(Duration.ofMinutes(30)));

        assertThat(evaluator.evaluate(failureRateOver(topic, 20d), NOW))
                .as("a window detector is a statement about a time range, not about all history")
                .isEmpty();
    }

    @Test
    @DisplayName("an empty window raises nothing rather than dividing by zero")
    void handlesEmptyWindow() {
        assertThat(evaluator.evaluate(failureRateOver(uniqueTopic(), 20d), NOW)).isEmpty();
    }

    @Test
    @DisplayName("the alert carries the evidence that produced it")
    void alertExplainsItself() {
        String topic = uniqueTopic();
        record(topic, "eu-west", true, 3);
        record(topic, "eu-west", false, 1);

        Alert alert = evaluator.evaluate(failureRateOver(topic, 20d), NOW).get(0);

        assertThat(alert.details())
                .containsEntry("function", "PERCENTAGE")
                .containsEntry("threshold", 20d)
                .containsEntry("comparison", ">")
                .containsEntry("sampleSize", 4)
                .containsKeys("windowFrom", "windowTo");
    }

    private WindowDetector failureRateOver(String topic, double threshold) {
        return new WindowDetector(
                "test-rate", "Failure rate", topic,
                AggregationFunction.PERCENTAGE, "failed", List.of("region"),
                ThresholdOperator.GREATER_THAN, threshold,
                Duration.ofMinutes(5), "CRITICAL", true);
    }

    private void record(String topic, String region, boolean failed, int count) {
        recordAt(topic, region, failed, count, NOW.minus(Duration.ofMinutes(1)));
    }

    private void recordAt(String topic, String region, boolean failed, int count, Instant at) {
        for (int i = 0; i < count; i++) {
            store.record(Event.of(topic, region, at,
                    Map.of("region", region, "failed", failed ? 1 : 0)));
        }
    }

    /** A fresh topic per test, so the shared in-memory store cannot leak between them. */
    private String uniqueTopic() {
        return "topic-" + UUID.randomUUID();
    }
}
