package io.portfolio.correlation.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.portfolio.correlation.correlate.CaseRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs an {@link ActionDefinition} against a case.
 *
 * <p>Three stages, each deliberately separable:
 *
 * <ol>
 *   <li><b>Bind inputs</b> — evaluate each input expression against the case.</li>
 *   <li><b>Call</b> — substitute those values into the URL and body, and send.</li>
 *   <li><b>Extract outputs</b> — pull named values out of the response by JSON pointer, so a
 *       downstream step can use them without parsing anything.</li>
 * </ol>
 *
 * <p>Failures are returned, not thrown. An action is an integration with someone else's system, and
 * those fail routinely — a case whose action failed is still a case, and the engine has to keep
 * running.
 */
@Component
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final ExpressionParser parser = new SpelExpressionParser();

    public ActionExecutor(RestClient.Builder restClientBuilder, ObjectMapper mapper) {
        this.restClient = restClientBuilder.build();
        this.mapper = mapper;
    }

    public ActionResult execute(ActionDefinition definition, CaseRecord record) {
        Map<String, Object> inputs;
        try {
            inputs = bindInputs(definition, record);
        } catch (RuntimeException e) {
            log.error("Action '{}' could not bind its inputs: {}", definition.name(), e.getMessage());
            return ActionResult.failed(definition.name(), "Input binding failed: " + e.getMessage());
        }

        try {
            ResponseEntity<String> response = send(definition, inputs);
            int status = response.getStatusCode().value();
            boolean successful = definition.successStatuses().contains(status);

            Map<String, Object> outputs = successful
                    ? extractOutputs(definition, response.getBody())
                    : Map.of();

            log.info("Action '{}' on case {} → {} {}", definition.name(), record.id(),
                    status, successful ? outputs : "(failed)");

            return new ActionResult(definition.name(), successful, status, inputs, outputs,
                    successful ? null : "Unexpected status " + status);

        } catch (RuntimeException e) {
            log.error("Action '{}' on case {} failed", definition.name(), record.id(), e);
            return ActionResult.failed(definition.name(), e.getMessage());
        }
    }

    private Map<String, Object> bindInputs(ActionDefinition definition, CaseRecord record) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("case", record);

        Map<String, Object> inputs = new LinkedHashMap<>();
        definition.inputs().forEach((name, expression) ->
                inputs.put(name, parser.parseExpression(expression).getValue(context)));
        return inputs;
    }

    private ResponseEntity<String> send(ActionDefinition definition, Map<String, Object> inputs) {
        RestClient.RequestBodySpec request = restClient
                .method(HttpMethod.valueOf(definition.method().toUpperCase()))
                .uri(substitute(definition.url(), inputs))
                .accept(MediaType.APPLICATION_JSON);

        definition.headers().forEach(request::header);

        if (definition.body() != null && !definition.body().isBlank()) {
            request.contentType(MediaType.APPLICATION_JSON);
            request.body(substitute(definition.body(), inputs));
        }

        return request.retrieve()
                // Without this, a 4xx or 5xx becomes an exception and the status code is lost.
                // The definition decides what counts as success, so every response has to come back.
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);
    }

    private Map<String, Object> extractOutputs(ActionDefinition definition, String body) {
        if (definition.outputs().isEmpty() || body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        try {
            JsonNode root = mapper.readTree(body);
            definition.outputs().forEach((name, pointer) -> {
                JsonNode node = root.at(pointer);
                outputs.put(name, node.isMissingNode() ? null : node.asText());
            });
        } catch (Exception e) {
            log.warn("Action '{}' returned a body that could not be parsed: {}",
                    definition.name(), e.getMessage());
        }
        return outputs;
    }

    /** Replaces {@code {name}} placeholders with bound input values. */
    private String substitute(String template, Map<String, Object> inputs) {
        String result = template;
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            result = result.replace("{" + input.getKey() + "}", String.valueOf(input.getValue()));
        }
        return result;
    }
}
