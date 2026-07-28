package io.portfolio.correlation.correlate;

import io.portfolio.correlation.alert.Alert;
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
 * The filter/classifier pair, which is where correlation is won or lost.
 *
 * <p>Each test tags its alerts with a marker and gives its case types a filter that matches only
 * that marker. Case types accumulate in the shared registry across tests, and a filter of
 * {@code true} would otherwise let every test's rules claim every other test's alerts.
 */
@SpringBootTest
class CorrelationEngineTest {

    @Autowired
    private CorrelationEngine engine;

    @Autowired
    private CaseTypeRegistry caseTypes;

    @Autowired
    private CaseRepository cases;

    @BeforeEach
    void reset() {
        cases.clear();
    }

    @Test
    @DisplayName("alerts sharing a classifier collapse into one case")
    void groupsByClassifier() {
        String marker = "group-test";
        caseTypes.save(byRegion(marker));

        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));
        engine.correlate(alert(marker, "HIGH", "eu-west", "refunds"));
        engine.correlate(alert(marker, "LOW", "eu-west", "payouts"));

        List<CaseRecord> open = casesOfType(marker);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).alertCount())
                .as("three alerts about one region is one problem")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a different classifier opens a different case")
    void separatesByClassifier() {
        String marker = "separate-test";
        caseTypes.save(byRegion(marker));

        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));
        engine.correlate(alert(marker, "HIGH", "us-east", "checkout"));

        assertThat(casesOfType(marker))
                .as("two regions are two cases")
                .hasSize(2);
    }

    @Test
    @DisplayName("the filter decides what a case type ignores")
    void filterExcludesNonMatchingAlerts() {
        String marker = "critical-only";
        caseTypes.save(new CaseType(marker, marker,
                matches(marker) + " and #alert.severity == 'CRITICAL'",
                "#alert.attribute('service')",
                "P1", AutoClosePolicy.DISABLED, 0, Duration.ofHours(1)));

        engine.correlate(alert(marker, "LOW", "eu-west", "checkout"));
        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));

        assertThat(casesOfType(marker)).isEmpty();

        engine.correlate(alert(marker, "CRITICAL", "eu-west", "checkout"));

        assertThat(casesOfType(marker)).hasSize(1);
    }

    @Test
    @DisplayName("one alert can belong to several case types at once")
    void oneAlertCanFeedSeveralCaseTypes() {
        String marker = "multi";
        caseTypes.save(byRegion(marker + "-region", marker));
        caseTypes.save(new CaseType(marker + "-service", "By service",
                matches(marker), "#alert.attribute('service')",
                "P2", AutoClosePolicy.DISABLED, 0, Duration.ofHours(1)));

        List<CaseRecord> touched = engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"))
                .stream()
                .filter(record -> record.caseTypeId().startsWith(marker))
                .toList();

        assertThat(touched)
                .as("the platform team groups by region, the service owner by service — both are valid")
                .hasSize(2);
        assertThat(touched.stream().map(CaseRecord::classifier))
                .containsExactlyInAnyOrder("eu-west", "checkout");
    }

    @Test
    @DisplayName("an alert that cannot be classified is not correlated")
    void skipsUnclassifiableAlerts() {
        String marker = "needs-tenant";
        caseTypes.save(new CaseType(marker, marker, matches(marker),
                "#alert.attribute('tenant')",
                "P3", AutoClosePolicy.DISABLED, 0, Duration.ofHours(1)));

        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));

        assertThat(casesOfType(marker))
                .as("a blank classifier would put every unrelated alert in one catch-all case")
                .isEmpty();
    }

    @Test
    @DisplayName("a broken expression takes out its own case type, not the others")
    void isolatesBrokenExpressions() {
        String marker = "isolation";
        caseTypes.save(new CaseType(marker + "-broken", "Broken",
                "#alert.thisMethodDoesNotExist()", "#alert.attribute('region')",
                "P3", AutoClosePolicy.DISABLED, 0, Duration.ofHours(1)));
        caseTypes.save(byRegion(marker + "-healthy", marker));

        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));

        assertThat(casesOfType(marker + "-broken")).isEmpty();
        assertThat(casesOfType(marker + "-healthy"))
                .as("one bad rule must not stop the rest of the engine working")
                .hasSize(1);
    }

    @Test
    @DisplayName("a closed case is not reopened; a recurrence gets its own case")
    void recurrenceOpensANewCase() {
        String marker = "recurrence";
        caseTypes.save(byRegion(marker));
        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));
        casesOfType(marker).get(0).close("done", Instant.now());

        engine.correlate(alert(marker, "HIGH", "eu-west", "checkout"));

        List<CaseRecord> all = cases.all().stream()
                .filter(record -> record.caseTypeId().equals(marker))
                .toList();
        assertThat(all)
                .as("reopening would hide that the problem happened twice")
                .hasSize(2);
    }

    // --- helpers -------------------------------------------------------------------------------

    private CaseType byRegion(String id) {
        return byRegion(id, id);
    }

    private CaseType byRegion(String id, String marker) {
        return new CaseType(id, id, matches(marker), "#alert.attribute('region')",
                "P2", AutoClosePolicy.DISABLED, 3, Duration.ofMinutes(30));
    }

    private String matches(String marker) {
        return "#alert.attribute('marker') == '" + marker + "'";
    }

    private List<CaseRecord> casesOfType(String caseTypeId) {
        return cases.openCases().stream()
                .filter(record -> record.caseTypeId().equals(caseTypeId))
                .toList();
    }

    /**
     * Built directly rather than published, so correlation is driven exactly once per alert and
     * these tests stay about the filter/classifier logic alone.
     */
    private Alert alert(String marker, String severity, String region, String service) {
        return Alert.raise("detector", "Test detector", "payments", severity,
                region + "/" + service,
                Map.of("marker", marker, "region", region, "service", service),
                Map.of());
    }
}
