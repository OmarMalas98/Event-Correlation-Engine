package io.portfolio.correlation.correlate;

public enum CaseStatus {

    /** Open and gathering alerts. */
    OPEN,

    /** Closed. Alerts that would have joined it open a new case instead. */
    CLOSED
}
