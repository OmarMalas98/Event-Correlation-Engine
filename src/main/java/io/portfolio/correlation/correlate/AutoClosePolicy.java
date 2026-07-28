package io.portfolio.correlation.correlate;

/**
 * When a quiet case should close itself.
 *
 * <p>Cases that stay open after the problem has gone are worse than useless: they train people to
 * ignore the list. But closing too eagerly loses the thread of an incident that is still unfolding,
 * so the right rule depends on the kind of problem, and is therefore per case type.
 *
 * <p>Every policy is additionally gated on all the case's alerts being resolved. A case with a live
 * alert never auto-closes, whatever the policy says.
 */
public enum AutoClosePolicy {

    /** Never close automatically. */
    DISABLED,

    /** Close once the case has gathered fewer than the configured number of alerts. */
    COUNT,

    /** Close once the case is older than the configured duration. */
    DURATION,

    /** Close only when both conditions hold — the conservative choice. */
    BOTH,

    /** Close as soon as either condition holds — the aggressive one. */
    ANY
}
