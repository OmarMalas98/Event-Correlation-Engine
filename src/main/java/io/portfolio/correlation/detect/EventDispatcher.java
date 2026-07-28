package io.portfolio.correlation.detect;

import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.alert.AlertPublisher;
import io.portfolio.correlation.stream.Event;
import io.portfolio.correlation.stream.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runs every active event detector against each arriving event, and retains the event for the
 * window detectors to query later.
 *
 * <p>Parsed expressions are cached. Detector conditions are stable strings evaluated on every event
 * of a topic, so re-parsing per event would put an expression parser on the hot path for no reason.
 */
@Component
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final DetectorRegistry registry;
    private final EventStore store;
    private final AlertPublisher alerts;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentMap<String, Expression> compiled = new ConcurrentHashMap<>();

    public EventDispatcher(DetectorRegistry registry, EventStore store, AlertPublisher alerts) {
        this.registry = registry;
        this.store = store;
        this.alerts = alerts;
    }

    public void dispatch(Event event) {
        store.record(event);

        for (EventDetector detector : registry.activeEventDetectorsFor(event.topic())) {
            if (matches(detector, event)) {
                alerts.publish(toAlert(detector, event));
            }
        }
    }

    private boolean matches(EventDetector detector, Event event) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("event", event);
            Object result = compile(detector.condition()).getValue(context);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException e) {
            // A broken expression is a configuration error, not a stream error. Log it and move on:
            // taking the detector out of service would be a silent failure, and failing the whole
            // dispatch would let one bad rule stop every other detector on the topic.
            log.error("Detector '{}' has an unusable condition [{}]: {}",
                    detector.name(), detector.condition(), e.getMessage());
            return false;
        }
    }

    private Alert toAlert(EventDetector detector, Event event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (String field : detector.carryFields()) {
            attributes.put(field, event.attributes().get(field));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", event.id());
        details.put("condition", detector.condition());
        details.put("source", event.source());

        return Alert.raise(detector.id(), detector.name(), detector.topic(),
                detector.severity(), event.source(), attributes, details);
    }

    private Expression compile(String condition) {
        return compiled.computeIfAbsent(condition, parser::parseExpression);
    }
}
