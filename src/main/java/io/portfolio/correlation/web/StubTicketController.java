package io.portfolio.correlation.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for whatever external system a case's action would really call.
 *
 * <p>Exists so the declarative-action feature is demonstrable without depending on a real ticketing
 * API being reachable.
 */
@RestController
public class StubTicketController {

    private static final Logger log = LoggerFactory.getLogger(StubTicketController.class);

    private final AtomicInteger sequence = new AtomicInteger(1000);

    @PostMapping("/stub/tickets")
    public ResponseEntity<Object> create(@RequestBody JsonNode body) {
        String id = "TCK-" + sequence.incrementAndGet();
        log.info("Stub ticketing system opened {} from {}", id, body);
        return ResponseEntity.status(201).body(Map.of(
                "id", id,
                "status", "open",
                "receivedSummary", body.path("summary").asText("")));
    }
}
