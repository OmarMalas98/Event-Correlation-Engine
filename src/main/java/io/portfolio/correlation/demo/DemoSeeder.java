package io.portfolio.correlation.demo;

import io.portfolio.correlation.correlate.AutoClosePolicy;
import io.portfolio.correlation.correlate.CaseType;
import io.portfolio.correlation.correlate.CaseTypeRegistry;
import io.portfolio.correlation.detect.AggregationFunction;
import io.portfolio.correlation.detect.DetectorRegistry;
import io.portfolio.correlation.detect.EventDetector;
import io.portfolio.correlation.detect.ThresholdOperator;
import io.portfolio.correlation.detect.WindowDetector;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * A worked example: two detectors and two case types over a payments stream.
 *
 * <p>Chosen to show the two axes the engine works on. The detectors demonstrate per-event versus
 * per-window detection; the case types demonstrate how much the classifier changes the outcome —
 * the same alerts group by region for one team and by service for another.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    public static final String TOPIC = "payments";

    private final DetectorRegistry detectors;
    private final CaseTypeRegistry caseTypes;

    public DemoSeeder(DetectorRegistry detectors, CaseTypeRegistry caseTypes) {
        this.detectors = detectors;
        this.caseTypes = caseTypes;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedDetectors();
        seedCaseTypes();
    }

    private void seedDetectors() {
        // Per event: one transaction that took absurdly long is worth knowing about on its own.
        detectors.save(new EventDetector(
                "slow-settlement",
                "Slow settlement",
                TOPIC,
                "#event.number('duration_ms') > 3000",
                "HIGH",
                List.of("region", "service"),
                true));

        // Per window: no single failure is remarkable, but 20% of them failing is.
        detectors.save(new WindowDetector(
                "elevated-failure-rate",
                "Elevated failure rate",
                TOPIC,
                AggregationFunction.PERCENTAGE,
                "failed",
                List.of("region", "service"),
                ThresholdOperator.GREATER_THAN,
                20d,
                Duration.ofMinutes(5),
                "CRITICAL",
                true));
    }

    private void seedCaseTypes() {
        // The platform team owns regions. Everything from a region belongs in one case.
        caseTypes.save(new CaseType(
                "regional-health",
                "Regional health",
                "true",
                "#alert.attribute('region')",
                "P2",
                AutoClosePolicy.ANY,
                3,
                Duration.ofMinutes(30)));

        // Service owners only care about the critical ones, grouped their way.
        caseTypes.save(new CaseType(
                "service-degradation",
                "Service degradation",
                "#alert.severity == 'CRITICAL'",
                "#alert.attribute('service')",
                "P1",
                AutoClosePolicy.DURATION,
                0,
                Duration.ofHours(2)));
    }
}
