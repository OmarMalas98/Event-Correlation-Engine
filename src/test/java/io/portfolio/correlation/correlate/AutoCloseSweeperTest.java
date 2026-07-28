package io.portfolio.correlation.correlate;

import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.alert.AlertPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-close, including the gate that overrides every policy: a case with a live alert stays open.
 *
 * <p>Time is passed in rather than waited for — a duration policy measured in hours is otherwise
 * untestable.
 */
@SpringBootTest
class AutoCloseSweeperTest {

    @Autowired
    private AutoCloseSweeper sweeper;

    @Autowired
    private CaseTypeRegistry caseTypes;

    @Autowired
    private CaseRepository cases;

    @Autowired
    private AlertPublisher alerts;

    @BeforeEach
    void reset() {
        cases.clear();
    }

    @Test
    @DisplayName("a case with an unresolved alert never closes, whatever the policy says")
    void unresolvedAlertsBlockClosing() {
        CaseType type = caseType("blocked", AutoClosePolicy.ANY, 100, Duration.ofSeconds(1));
        CaseRecord record = correlateOne(type, "eu-west", false);

        sweeper.sweep(Instant.now().plus(Duration.ofDays(1)));

        assertThat(record.isOpen())
                .as("age is a guess that the problem is over; a live alert is evidence that it is not")
                .isTrue();
    }

    @Test
    @DisplayName("COUNT closes a case that stayed small")
    void countPolicyClosesQuietCases() {
        CaseType type = caseType("count-quiet", AutoClosePolicy.COUNT, 3, Duration.ofHours(99));
        CaseRecord record = correlateOne(type, "eu-west", true);

        sweeper.sweep(Instant.now());

        assertThat(record.isOpen()).isFalse();
        assertThat(record.closedReason()).contains("resolved");
    }

    @Test
    @DisplayName("COUNT leaves a case that gathered too many alerts")
    void countPolicyKeepsBusyCasesOpen() {
        CaseType type = caseType("count-busy", AutoClosePolicy.COUNT, 2, Duration.ofHours(99));
        CaseRecord record = correlateMany(type, "us-east", 4);

        sweeper.sweep(Instant.now());

        assertThat(record.isOpen())
                .as("four alerts is above the threshold of two — this one wants a human")
                .isTrue();
    }

    @Test
    @DisplayName("DURATION closes only once the case is old enough")
    void durationPolicyWaitsForAge() {
        CaseType type = caseType("duration", AutoClosePolicy.DURATION, 0, Duration.ofHours(2));
        CaseRecord record = correlateOne(type, "ap-south", true);

        sweeper.sweep(Instant.now().plus(Duration.ofMinutes(30)));
        assertThat(record.isOpen()).isTrue();

        sweeper.sweep(Instant.now().plus(Duration.ofHours(3)));
        assertThat(record.isOpen()).isFalse();
    }

    @Test
    @DisplayName("BOTH needs the case to be quiet and old")
    void bothPolicyNeedsEveryCondition() {
        CaseType type = caseType("both", AutoClosePolicy.BOTH, 2, Duration.ofHours(2));
        CaseRecord busy = correlateMany(type, "eu-central", 5);

        sweeper.sweep(Instant.now().plus(Duration.ofHours(3)));

        assertThat(busy.isOpen())
                .as("old enough, but far too busy — BOTH is the conservative policy")
                .isTrue();
    }

    @Test
    @DisplayName("ANY closes as soon as either condition holds")
    void anyPolicyNeedsOnlyOneCondition() {
        CaseType type = caseType("any", AutoClosePolicy.ANY, 2, Duration.ofHours(2));
        CaseRecord busy = correlateMany(type, "sa-east", 5);

        sweeper.sweep(Instant.now().plus(Duration.ofHours(3)));

        assertThat(busy.isOpen())
                .as("too busy to close on count, but old enough to close on duration")
                .isFalse();
    }

    @Test
    @DisplayName("DISABLED never closes anything")
    void disabledPolicyNeverCloses() {
        CaseType type = caseType("disabled", AutoClosePolicy.DISABLED, 100, Duration.ofSeconds(1));
        CaseRecord record = correlateOne(type, "eu-north", true);

        sweeper.sweep(Instant.now().plus(Duration.ofDays(30)));

        assertThat(record.isOpen()).isTrue();
    }

    // --- helpers -------------------------------------------------------------------------------

    private CaseType caseType(String id, AutoClosePolicy policy, int count, Duration after) {
        return caseTypes.save(new CaseType(id, id, "#alert.attribute('caseType') == '" + id + "'",
                "#alert.attribute('region')", "P2", policy, count, after));
    }

    private CaseRecord correlateOne(CaseType type, String region, boolean resolve) {
        return correlate(type, region, 1, resolve);
    }

    private CaseRecord correlateMany(CaseType type, String region, int count) {
        return correlate(type, region, count, true);
    }

    private CaseRecord correlate(CaseType type, String region, int count, boolean resolve) {
        for (int i = 0; i < count; i++) {
            Alert alert = Alert.raise("d", "Detector", "payments", "HIGH", region,
                    Map.of("region", region, "caseType", type.id()), Map.of());
            alerts.publish(alert);
            if (resolve) {
                alerts.resolve(alert.id());
            }
        }
        List<CaseRecord> matching = cases.openCases().stream()
                .filter(record -> record.caseTypeId().equals(type.id()))
                .toList();
        assertThat(matching).as("expected exactly one case for %s/%s", type.id(), region).hasSize(1);
        return matching.get(0);
    }
}
