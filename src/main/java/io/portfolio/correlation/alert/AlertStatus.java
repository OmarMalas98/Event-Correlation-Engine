package io.portfolio.correlation.alert;

public enum AlertStatus {

    /** Raised and not yet cleared. */
    ACTIVE,

    /** The underlying condition has gone away. */
    RESOLVED
}
