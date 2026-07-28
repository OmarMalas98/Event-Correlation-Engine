package io.portfolio.correlation.web;

import io.portfolio.correlation.action.ActionDefinition;
import io.portfolio.correlation.action.ActionExecutor;
import io.portfolio.correlation.action.ActionResult;
import io.portfolio.correlation.alert.Alert;
import io.portfolio.correlation.alert.AlertPublisher;
import io.portfolio.correlation.correlate.CaseRecord;
import io.portfolio.correlation.correlate.CaseRepository;
import io.portfolio.correlation.correlate.CaseTypeRegistry;
import io.portfolio.correlation.detect.Detector;
import io.portfolio.correlation.detect.DetectorRegistry;
import io.portfolio.correlation.detect.SubscriptionManager;
import io.portfolio.correlation.demo.EventSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A window onto the engine, and the controls the demo needs.
 */
@RestController
public class EngineController {

    private final DetectorRegistry detectors;
    private final SubscriptionManager subscriptions;
    private final AlertPublisher alerts;
    private final CaseRepository cases;
    private final CaseTypeRegistry caseTypes;
    private final EventSimulator simulator;
    private final ActionExecutor actions;

    public EngineController(DetectorRegistry detectors, SubscriptionManager subscriptions,
                            AlertPublisher alerts, CaseRepository cases, CaseTypeRegistry caseTypes,
                            EventSimulator simulator, ActionExecutor actions) {
        this.detectors = detectors;
        this.subscriptions = subscriptions;
        this.alerts = alerts;
        this.cases = cases;
        this.caseTypes = caseTypes;
        this.simulator = simulator;
        this.actions = actions;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "consumedTopics", subscriptions.consumedTopics(),
                "detectors", detectors.all().size(),
                "activeDetectors", detectors.all().stream().filter(Detector::active).count(),
                "caseTypes", caseTypes.all().size(),
                "alerts", alerts.all().size(),
                "activeAlerts", alerts.activeCount(),
                "openCases", cases.openCases().size(),
                "totalCases", cases.all().size());
    }

    @GetMapping("/detectors")
    public List<Map<String, Object>> detectors() {
        return detectors.all().stream().map(detector -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", detector.id());
            view.put("name", detector.name());
            view.put("kind", detector instanceof io.portfolio.correlation.detect.EventDetector
                    ? "event" : "window");
            view.put("topic", detector.topic());
            view.put("severity", detector.severity());
            view.put("active", detector.active());
            return view;
        }).toList();
    }

    @PostMapping("/detectors/{id}/active")
    public ResponseEntity<Object> setActive(@PathVariable String id, @RequestParam boolean value) {
        return detectors.setActive(id, value)
                .<ResponseEntity<Object>>map(detector -> ResponseEntity.ok(Map.of(
                        "detector", detector.name(),
                        "active", detector.active(),
                        "consumedTopics", subscriptions.consumedTopics())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        return alerts.all().stream()
                .sorted(java.util.Comparator.comparing(Alert::raisedAt))
                .map(alert -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", alert.id());
                    view.put("detector", alert.detectorName());
                    view.put("severity", alert.severity());
                    view.put("source", alert.source());
                    view.put("status", alert.status());
                    view.put("attributes", alert.attributes());
                    view.put("details", alert.details());
                    return view;
                }).toList();
    }

    @PostMapping("/alerts/{id}/resolve")
    public ResponseEntity<Object> resolve(@PathVariable String id) {
        return alerts.resolve(id)
                .<ResponseEntity<Object>>map(alert -> ResponseEntity.ok(Map.of(
                        "id", alert.id(), "status", alert.status())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/alerts/resolve-all")
    public Map<String, Object> resolveAll() {
        long resolved = alerts.all().stream()
                .filter(alert -> !alert.isResolved())
                .map(alert -> alerts.resolve(alert.id()))
                .count();
        return Map.of("resolved", resolved);
    }

    @GetMapping("/cases")
    public List<Map<String, Object>> cases() {
        return cases.all().stream().map(this::view).toList();
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<Object> caseById(@PathVariable String id) {
        return cases.find(id)
                .<ResponseEntity<Object>>map(record -> ResponseEntity.ok(view(record)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Runs a declarative action against a case. The definition is posted rather than stored, which
     * makes the "an integration is just data" idea directly demonstrable with curl.
     */
    @PostMapping("/cases/{id}/actions")
    public ResponseEntity<Object> runAction(@PathVariable String id, @RequestBody ActionDefinition definition) {
        return cases.find(id)
                .<ResponseEntity<Object>>map(record -> {
                    ActionResult result = actions.execute(definition, record);
                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/simulate/healthy")
    public Map<String, Object> healthy(@RequestParam(defaultValue = "40") int count) {
        return Map.of("published", simulator.publishHealthy(count));
    }

    @PostMapping("/simulate/incident")
    public Map<String, Object> incident(@RequestParam(defaultValue = "eu-west") String region,
                                        @RequestParam(defaultValue = "checkout") String service,
                                        @RequestParam(defaultValue = "12") int count) {
        return Map.of("published", simulator.publishIncident(region, service, count));
    }

    private Map<String, Object> view(CaseRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.id());
        view.put("caseType", record.caseTypeName());
        view.put("classifier", record.classifier());
        view.put("priority", record.priority());
        view.put("status", record.status());
        view.put("alertCount", record.alertCount());
        view.put("detectors", record.detectorNames());
        view.put("openedAt", record.openedAt());
        view.put("lastAlertAt", record.lastAlertAt());
        if (record.closedAt() != null) {
            view.put("closedAt", record.closedAt());
            view.put("closedReason", record.closedReason());
        }
        return view;
    }
}
