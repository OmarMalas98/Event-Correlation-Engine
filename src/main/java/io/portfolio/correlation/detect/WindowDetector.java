package io.portfolio.correlation.detect;

import java.time.Duration;
import java.util.List;

/**
 * Fires when an aggregate over a time window crosses a threshold.
 *
 * <p>Evaluated on a schedule rather than per event. Events in the window are grouped by
 * {@code dimensions} — so "error rate above 5%" becomes "error rate above 5% <em>per region, per
 * service</em>", which is the difference between an alert you can act on and one that tells you
 * only that something, somewhere, is wrong.
 *
 * @param function   how the group is reduced to a number
 * @param factField  the attribute the function reads; ignored by {@code COUNT}
 * @param dimensions attributes to group by; empty means one group over the whole window
 * @param operator   how the result is compared to {@code threshold}
 */
public record WindowDetector(
        String id,
        String name,
        String topic,
        AggregationFunction function,
        String factField,
        List<String> dimensions,
        ThresholdOperator operator,
        double threshold,
        Duration window,
        String severity,
        boolean active
) implements Detector {

    @Override
    public Detector activated(boolean active) {
        return new WindowDetector(id, name, topic, function, factField, dimensions,
                operator, threshold, window, severity, active);
    }
}
