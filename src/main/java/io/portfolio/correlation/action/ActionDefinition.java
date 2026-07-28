package io.portfolio.correlation.action;

import java.util.List;
import java.util.Map;

/**
 * An outbound HTTP call, described as data rather than written as code.
 *
 * <p>This is the "no-code integration" idea. Every organisation this kind of engine lands in wants
 * a case to do something in <em>their</em> ticketing system, <em>their</em> chat, <em>their</em>
 * inventory API. Writing an integration per customer does not scale; describing the call as a row
 * someone can edit does.
 *
 * @param inputs          named values pulled off the case with SpEL, e.g.
 *                        {@code region -> #case.classifier()}
 * @param body            request body template; {@code {name}} placeholders are filled from
 *                        {@code inputs}
 * @param outputs         values pulled back out of the response by JSON pointer, e.g.
 *                        {@code ticketId -> /id}
 * @param successStatuses status codes treated as success; anything else is a failure
 */
public record ActionDefinition(
        String id,
        String name,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> inputs,
        String body,
        Map<String, String> outputs,
        List<Integer> successStatuses
) {

    public ActionDefinition {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        successStatuses = successStatuses == null || successStatuses.isEmpty()
                ? List.of(200, 201, 202, 204)
                : List.copyOf(successStatuses);
    }
}
