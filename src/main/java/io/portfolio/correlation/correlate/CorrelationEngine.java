package io.portfolio.correlation.correlate;

import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.alert.AlertPublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Collapses a stream of alerts into a much smaller number of cases.
 *
 * <p>This is the stage that makes the difference between an alert list nobody reads and a queue of
 * work someone can actually do. For each alert, every case type asks two questions:
 *
 * <ol>
 *   <li><b>Is this mine?</b> — the filter expression. If false, this case type ignores the alert.</li>
 *   <li><b>Which instance?</b> — the classifier expression. Its value <em>is</em> the case's
 *       identity. Same value, same case.</li>
 * </ol>
 *
 * <p>Note that an alert can match several case types. That is intentional rather than an oversight:
 * a single failure may legitimately be both a customer-impact case for support and a capacity case
 * for the platform team, and forcing a choice between them serves neither.
 */
@Component
public class CorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);

    private final CaseTypeRegistry caseTypes;
    private final CaseRepository cases;
    private final AlertPublisher alerts;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentMap<String, Expression> compiled = new ConcurrentHashMap<>();

    public CorrelationEngine(CaseTypeRegistry caseTypes, CaseRepository cases, AlertPublisher alerts) {
        this.caseTypes = caseTypes;
        this.cases = cases;
        this.alerts = alerts;
    }

    @PostConstruct
    void subscribeToAlerts() {
        alerts.subscribe(this::correlate);
    }

    /**
     * Routes one alert into every case type that claims it.
     *
     * @return the cases the alert was attached to
     */
    public List<CaseRecord> correlate(Alert alert) {
        return caseTypes.all().stream()
                .map(caseType -> apply(caseType, alert))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CaseRecord> apply(CaseType caseType, Alert alert) {
        try {
            if (!Boolean.TRUE.equals(evaluate(caseType.filter(), alert, Boolean.class))) {
                return Optional.empty();
            }

            String classifier = evaluate(caseType.classifier(), alert, String.class);
            if (classifier == null || classifier.isBlank()) {
                // An alert that cannot be classified cannot be correlated: it would either land in
                // a catch-all case with everything else, or open an unbounded stream of anonymous
                // ones. Better to say so loudly.
                log.warn("Alert '{}' produced no classifier for case type '{}' — not correlated",
                        alert.detectorName(), caseType.name());
                return Optional.empty();
            }

            CaseRecord record = cases.findOpen(caseType.id(), classifier)
                    .orElseGet(() -> open(caseType, classifier));
            record.attach(alert);

            log.info("Alert '{}' → case {} [{}/{}] now holding {} alert(s)",
                    alert.detectorName(), record.id(), caseType.name(), classifier, record.alertCount());
            return Optional.of(record);

        } catch (RuntimeException e) {
            log.error("Case type '{}' failed on alert '{}': {}",
                    caseType.name(), alert.detectorName(), e.getMessage());
            return Optional.empty();
        }
    }

    private CaseRecord open(CaseType caseType, String classifier) {
        CaseRecord record = new CaseRecord(caseType, classifier, Instant.now());
        cases.save(record);
        log.info("Opened case {} [{}/{}]", record.id(), caseType.name(), classifier);
        return record;
    }

    private <T> T evaluate(String expression, Alert alert, Class<T> type) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("alert", alert);
        return compiled.computeIfAbsent(expression, parser::parseExpression).getValue(context, type);
    }
}
