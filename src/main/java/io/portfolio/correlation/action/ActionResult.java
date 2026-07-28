package io.portfolio.correlation.action;

import java.util.Map;

/**
 * What an action did.
 *
 * @param outputs values extracted from the response, per the definition's output bindings
 */
public record ActionResult(
        String actionName,
        boolean successful,
        int statusCode,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        String failure
) {

    public static ActionResult failed(String actionName, String failure) {
        return new ActionResult(actionName, false, 0, Map.of(), Map.of(), failure);
    }
}
