package io.portfolio.correlation.correlate;

import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.alert.AlertPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Closes cases that have gone quiet, according to their type's policy.
 *
 * <p>The gate that applies to every policy is the one that matters: <b>a case only closes when all
 * of its alerts are resolved</b>. Age and alert count are heuristics for "probably over"; an
 * unresolved alert is evidence that it is not. Closing over the top of one would quietly drop a
 * live problem out of the queue, which is the single worst thing this component could do.
 */
@Component
public class AutoCloseSweeper {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseSweeper.class);

    private final CaseRepository cases;
    private final CaseTypeRegistry caseTypes;
    private final AlertPublisher alerts;

    public AutoCloseSweeper(CaseRepository cases, CaseTypeRegistry caseTypes, AlertPublisher alerts) {
        this.cases = cases;
        this.caseTypes = caseTypes;
        this.alerts = alerts;
    }

    @Scheduled(fixedDelayString = "${correlation.auto-close-interval:PT10S}")
    public void sweep() {
        sweep(Instant.now());
    }

    /**
     * Runs one pass as at {@code now}, returning the cases it closed. Time is a parameter so the
     * duration policies can be tested without waiting for them.
     */
    public List<CaseRecord> sweep(Instant now) {
        return cases.openCases().stream()
                .filter(record -> shouldClose(record, now))
                .peek(record -> {
                    record.close(reasonFor(record), now);
                    log.info("Auto-closed case {} [{}/{}] — {}",
                            record.id(), record.caseTypeName(), record.classifier(), record.closedReason());
                })
                .toList();
    }

    private boolean shouldClose(CaseRecord record, Instant now) {
        CaseType caseType = caseTypes.find(record.caseTypeId()).orElse(null);
        if (caseType == null || caseType.autoClosePolicy() == AutoClosePolicy.DISABLED) {
            return false;
        }
        if (!allAlertsResolved(record)) {
            return false;
        }

        boolean quietEnough = record.alertCount() < caseType.autoCloseCount();
        boolean oldEnough = isOlderThan(record, caseType.autoCloseAfter(), now);

        return switch (caseType.autoClosePolicy()) {
            case COUNT -> quietEnough;
            case DURATION -> oldEnough;
            case BOTH -> quietEnough && oldEnough;
            case ANY -> quietEnough || oldEnough;
            case DISABLED -> false;
        };
    }

    /**
     * True only if every alert on the case is resolved. An alert the publisher no longer knows
     * about counts as unresolved — absence of evidence is not evidence of resolution.
     */
    private boolean allAlertsResolved(CaseRecord record) {
        return record.alertIds().stream()
                .map(alerts::find)
                .allMatch(found -> found.map(Alert::isResolved).orElse(false));
    }

    private boolean isOlderThan(CaseRecord record, Duration age, Instant now) {
        return age != null && record.openedAt().plus(age).isBefore(now);
    }

    private String reasonFor(CaseRecord record) {
        return "all " + record.alertCount() + " alert(s) resolved; policy satisfied";
    }
}
