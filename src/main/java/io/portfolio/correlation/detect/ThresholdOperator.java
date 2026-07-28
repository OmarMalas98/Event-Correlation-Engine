package io.portfolio.correlation.detect;

import java.util.function.DoubleBinaryOperator;

/**
 * How a window's aggregate is compared to its threshold.
 */
public enum ThresholdOperator {

    GREATER_THAN((actual, threshold) -> actual > threshold ? 1 : 0, ">"),
    GREATER_OR_EQUAL((actual, threshold) -> actual >= threshold ? 1 : 0, ">="),
    LESS_THAN((actual, threshold) -> actual < threshold ? 1 : 0, "<"),
    LESS_OR_EQUAL((actual, threshold) -> actual <= threshold ? 1 : 0, "<=");

    private final DoubleBinaryOperator test;
    private final String symbol;

    ThresholdOperator(DoubleBinaryOperator test, String symbol) {
        this.test = test;
        this.symbol = symbol;
    }

    public boolean breached(double actual, double threshold) {
        return test.applyAsDouble(actual, threshold) == 1;
    }

    public String symbol() {
        return symbol;
    }
}
