package io.portfolio.correlation.detect;

import io.portfolio.correlation.stream.Event;

import java.util.List;

/**
 * How a group of events is reduced to one number.
 */
public enum AggregationFunction {

    COUNT {
        @Override
        public double apply(List<Event> events, String factField) {
            return events.size();
        }
    },

    SUM {
        @Override
        public double apply(List<Event> events, String factField) {
            return events.stream().mapToDouble(event -> event.number(factField)).sum();
        }
    },

    AVERAGE {
        @Override
        public double apply(List<Event> events, String factField) {
            // Guarding the empty case here rather than relying on the caller: an empty group is a
            // normal occurrence at the edge of a window, and NaN would silently defeat every
            // threshold comparison downstream.
            return events.isEmpty() ? 0d
                    : events.stream().mapToDouble(event -> event.number(factField)).average().orElse(0d);
        }
    },

    MIN {
        @Override
        public double apply(List<Event> events, String factField) {
            return events.stream().mapToDouble(event -> event.number(factField)).min().orElse(0d);
        }
    },

    MAX {
        @Override
        public double apply(List<Event> events, String factField) {
            return events.stream().mapToDouble(event -> event.number(factField)).max().orElse(0d);
        }
    },

    /**
     * Share of the group, 0–100, whose fact field is non-zero.
     *
     * <p>This is the "rate" aggregate — set {@code factField} to a 0/1 flag such as {@code failed}
     * and the threshold becomes an error-rate alarm, which is usually what you want rather than a
     * raw failure count. A count of 50 failures means nothing without knowing whether the
     * denominator was 60 or 6,000,000.
     */
    PERCENTAGE {
        @Override
        public double apply(List<Event> events, String factField) {
            if (events.isEmpty()) {
                return 0d;
            }
            long matching = events.stream().filter(event -> event.number(factField) != 0d).count();
            return (matching * 100d) / events.size();
        }
    };

    public abstract double apply(List<Event> events, String factField);
}
