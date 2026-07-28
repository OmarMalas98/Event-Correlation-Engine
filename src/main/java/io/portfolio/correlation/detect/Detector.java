package io.portfolio.correlation.detect;

/**
 * A rule that turns events into alerts.
 *
 * <p>Two kinds, because there are two genuinely different questions to ask of a stream:
 *
 * <ul>
 *   <li>{@link EventDetector} — "is <em>this event</em> a problem?" Evaluated per event, as it
 *       arrives. Catches the single catastrophic record.</li>
 *   <li>{@link WindowDetector} — "is the <em>shape</em> of the last N minutes a problem?" Evaluated
 *       on a schedule over retained events. Catches the slow bleed that no individual event
 *       reveals — an error rate creeping up, a throughput collapse, a nightly total that is
 *       suddenly double.</li>
 * </ul>
 *
 * <p>Sealed so the evaluator must handle both: adding a third kind becomes a compile error rather
 * than a silently unevaluated detector.
 */
public sealed interface Detector permits EventDetector, WindowDetector {

    String id();

    String name();

    String topic();

    String severity();

    boolean active();

    Detector activated(boolean active);
}
