package io.portfolio.correlation.correlate;

import io.portfolio.correlation.alert.Alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A case: one problem, however many alerts it produced.
 *
 * <p>Mutable, unlike almost everything else here, because a case is a living thing — it accumulates
 * alerts over its lifetime and that is its entire purpose. Mutation is confined to synchronized
 * methods so a burst of alerts arriving at once cannot corrupt the list or lose an entry.
 */
public class CaseRecord {

    private final String id;
    private final String caseTypeId;
    private final String caseTypeName;
    private final String classifier;
    private final String priority;
    private final Instant openedAt;

    private final List<String> alertIds = new ArrayList<>();
    private final Set<String> detectorNames = new LinkedHashSet<>();

    private CaseStatus status = CaseStatus.OPEN;
    private Instant lastAlertAt;
    private Instant closedAt;
    private String closedReason;

    public CaseRecord(CaseType caseType, String classifier, Instant openedAt) {
        this.id = UUID.randomUUID().toString();
        this.caseTypeId = caseType.id();
        this.caseTypeName = caseType.name();
        this.classifier = classifier;
        this.priority = caseType.priority();
        this.openedAt = openedAt;
        this.lastAlertAt = openedAt;
    }

    public synchronized void attach(Alert alert) {
        alertIds.add(alert.id());
        detectorNames.add(alert.detectorName());
        lastAlertAt = alert.raisedAt();
    }

    public synchronized void close(String reason, Instant when) {
        this.status = CaseStatus.CLOSED;
        this.closedReason = reason;
        this.closedAt = when;
    }

    public synchronized boolean isOpen() {
        return status == CaseStatus.OPEN;
    }

    public synchronized List<String> alertIds() {
        return List.copyOf(alertIds);
    }

    public synchronized int alertCount() {
        return alertIds.size();
    }

    public synchronized Set<String> detectorNames() {
        return Set.copyOf(detectorNames);
    }

    public synchronized CaseStatus status() {
        return status;
    }

    public synchronized Instant lastAlertAt() {
        return lastAlertAt;
    }

    public synchronized Instant closedAt() {
        return closedAt;
    }

    public synchronized String closedReason() {
        return closedReason;
    }

    public String id() {
        return id;
    }

    public String caseTypeId() {
        return caseTypeId;
    }

    public String caseTypeName() {
        return caseTypeName;
    }

    public String classifier() {
        return classifier;
    }

    public String priority() {
        return priority;
    }

    public Instant openedAt() {
        return openedAt;
    }
}
