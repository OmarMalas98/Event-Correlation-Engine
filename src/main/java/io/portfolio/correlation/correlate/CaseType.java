package io.portfolio.correlation.correlate;

import java.time.Duration;

/**
 * A rule for turning alerts into cases.
 *
 * <p>Two expressions do the work, and the distinction between them is the core idea of the whole
 * correlation stage:
 *
 * <ul>
 *   <li><b>{@code filter}</b> — <i>does this alert belong to this kind of case at all?</i>
 *       A boolean, e.g. {@code #alert.severity == 'HIGH'}.</li>
 *   <li><b>{@code classifier}</b> — <i>which case instance?</i> A string whose value is the case's
 *       identity, e.g. {@code #alert.attribute('region')}. Two alerts producing the same classifier
 *       join the same case; a different value opens a new one.</li>
 * </ul>
 *
 * <p>Choosing the classifier is where correlation is won or lost. Too coarse — a constant — and
 * every unrelated problem lands in one case. Too fine — include a timestamp or an event id — and
 * every alert gets its own case, which is just the alert list with extra steps.
 *
 * @param autoCloseCount alert count below which a {@code COUNT}-style policy will close the case
 * @param autoCloseAfter age beyond which a {@code DURATION}-style policy will close the case
 */
public record CaseType(
        String id,
        String name,
        String filter,
        String classifier,
        String priority,
        AutoClosePolicy autoClosePolicy,
        int autoCloseCount,
        Duration autoCloseAfter
) {
}
